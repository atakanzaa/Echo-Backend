package com.echo.service;

import com.echo.domain.community.*;
import com.echo.domain.user.User;
import com.echo.dto.request.CreateCommentRequest;
import com.echo.dto.request.CreatePostRequest;
import com.echo.dto.response.CommunityPostResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.dto.response.PostCommentResponse;
import com.echo.event.CommentRepliedEvent;
import com.echo.event.PostCommentedEvent;
import com.echo.event.PostLikedEvent;
import com.echo.exception.ResourceNotFoundException;
import com.echo.exception.UnauthorizedException;
import com.echo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityPostRepository communityPostRepository;
    private final PostLikeRepository      postLikeRepository;
    private final PostCommentRepository   postCommentRepository;
    private final UserRepository          userRepository;
    private final FollowRepository        followRepository;
    private final CommentLikeRepository   commentLikeRepository;
    private final StorageService          storageService;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementService        achievementService;
    private final ApplicationEventPublisher eventPublisher;

    // ── Feed ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<CommunityPostResponse> getFeed(UUID userId, String tab, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<CommunityPost> posts = "following".equalsIgnoreCase(tab)
                ? getFollowingFeed(userId, pageable)
                : communityPostRepository.findByPublicPostTrueOrderByCreatedAtDesc(pageable);

        Set<UUID> pagePostIds = posts.getContent().stream()
                .map(CommunityPost::getId)
                .collect(Collectors.toSet());
        Set<UUID> likedPostIds = pagePostIds.isEmpty()
                ? Set.of()
                : postLikeRepository.findLikedPostIdsByUserIdAndPostIds(userId, pagePostIds);

        return PagedResponse.from(posts, p -> CommunityPostResponse.from(p, likedPostIds.contains(p.getId())));
    }

    private Page<CommunityPost> getFollowingFeed(UUID userId, Pageable pageable) {
        List<UUID> followingIds = followRepository.findFollowingIdsByFollowerId(userId);
        if (followingIds.isEmpty()) return Page.empty(pageable);
        return communityPostRepository.findByPublicPostTrueAndUserIdInOrderByCreatedAtDesc(followingIds, pageable);
    }

    // ── Single Post ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CommunityPostResponse getPost(UUID postId, UUID userId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        boolean liked = postLikeRepository.existsByPostIdAndUserId(postId, userId);
        return CommunityPostResponse.from(post, liked);
    }

    // ── Create Post ───────────────────────────────────────────────────────────

    @Transactional
    public CommunityPostResponse createPost(UUID userId, CreatePostRequest request, MultipartFile imageFile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String imageUrl = null;
        String content = request.content();
        String contentType = request.contentType() != null ? request.contentType() : "text";
        String badgeKey = normalizeBadgeKey(request.badgeKey());

        log.info("createPost: imageFile={} size={} contentType={} badgeKey={}",
                imageFile != null ? imageFile.getOriginalFilename() : "null",
                imageFile != null ? imageFile.getSize() : 0,
                imageFile != null ? imageFile.getContentType() : "null",
                badgeKey);

        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = storageService.uploadImage(imageFile);
            log.info("createPost: image uploaded → {}", imageUrl);
            contentType = "image";
        }

        if (badgeKey != null) {
            if (!userAchievementRepository.existsByUserIdAndBadgeKey(userId, badgeKey)) {
                throw new IllegalArgumentException("Badge must be earned before sharing: " + badgeKey);
            }
            if (content == null || content.isBlank()) {
                content = achievementService.generateShareText(badgeKey);
            }
            contentType = "achievement";
        }

        if (imageUrl == null && (content == null || content.isBlank())) {
            throw new IllegalArgumentException("Text post must have content");
        }

        CommunityPost post = CommunityPost.builder()
                .user(user)
                .content(content)
                .contentType(contentType)
                .emoji(request.emoji())
                .anonymous(request.isAnonymous())
                .imageUrl(imageUrl)
                .badgeKey(badgeKey)
                .build();
        return CommunityPostResponse.from(communityPostRepository.save(post), false);
    }

    // ── Delete Post ───────────────────────────────────────────────────────────

    @Transactional
    public void deletePost(UUID postId, UUID userId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (!post.getUser().getId().equals(userId))
            throw new UnauthorizedException("You do not have permission to delete this post");
        storageService.deleteImage(post.getImageUrl());
        communityPostRepository.delete(post);
    }

    // ── Post Like / Unlike ────────────────────────────────────────────────────

    @Transactional
    public void likePost(UUID postId, UUID userId) {
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) return;
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        try {
            postLikeRepository.saveAndFlush(PostLike.builder().post(post).user(user).build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate post like ignored: postId={} userId={}", postId, userId);
            return;
        }
        communityPostRepository.incrementLikesCount(postId);
        eventPublisher.publishEvent(new PostLikedEvent(userId, post.getUser().getId(), postId));
    }

    @Transactional
    public void unlikePost(UUID postId, UUID userId) {
        postLikeRepository.findByPostIdAndUserId(postId, userId).ifPresent(like -> {
            postLikeRepository.delete(like);
            communityPostRepository.decrementLikesCount(postId);
        });
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<PostCommentResponse> getComments(UUID postId, UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        var topLevel = postCommentRepository.findByPostIdAndParentIsNullOrderByCreatedAtAsc(postId, pageable);

        List<UUID> topLevelIds = topLevel.getContent().stream()
                .map(PostComment::getId).toList();
        if (topLevelIds.isEmpty()) {
            return PagedResponse.from(topLevel, c -> PostCommentResponse.from(c, false, Collections.emptyList()));
        }

        // batch-load all replies for this page's top-level comments (1 query)
        Map<UUID, List<PostComment>> repliesByParent = postCommentRepository
                .findByParentIdInOrderByCreatedAtAsc(topLevelIds).stream()
                .collect(Collectors.groupingBy(r -> r.getParent().getId()));

        // collect all comment IDs (top-level + replies) for batch like check
        List<UUID> allCommentIds = new ArrayList<>(topLevelIds);
        repliesByParent.values().forEach(replies ->
                replies.forEach(r -> allCommentIds.add(r.getId())));

        // batch-load liked status for all comments (1 query)
        Set<UUID> likedIds = commentLikeRepository.findCommentIdsLikedByUser(userId, allCommentIds);

        return PagedResponse.from(topLevel, comment -> {
            List<PostCommentResponse> replies = repliesByParent
                    .getOrDefault(comment.getId(), Collections.emptyList()).stream()
                    .map(reply -> PostCommentResponse.from(reply, likedIds.contains(reply.getId()), Collections.emptyList()))
                    .collect(Collectors.toList());
            return PostCommentResponse.from(comment, likedIds.contains(comment.getId()), replies);
        });
    }

    @Transactional
    public PostCommentResponse createComment(UUID postId, UUID userId, CreateCommentRequest request) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PostComment.PostCommentBuilder builder = PostComment.builder()
                .post(post).user(user).content(request.content());
        PostComment parentComment = null;

        if (request.parentId() != null) {
            parentComment = postCommentRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
            if (!parentComment.getPost().getId().equals(postId))
                throw new IllegalArgumentException("Parent comment does not belong to this post");
            if (parentComment.getParent() != null)
                throw new IllegalArgumentException("Cannot reply to a reply (max 1 level depth)");
            builder.parent(parentComment);
        }

        PostComment comment = postCommentRepository.save(builder.build());
        communityPostRepository.incrementCommentsCount(postId);

        if (parentComment == null) {
            eventPublisher.publishEvent(new PostCommentedEvent(
                    userId, post.getUser().getId(), postId, comment.getId(), post.isAnonymous()
            ));
        } else {
            eventPublisher.publishEvent(new CommentRepliedEvent(
                    userId, parentComment.getUser().getId(), postId, comment.getId(), post.isAnonymous()
            ));
        }
        return PostCommentResponse.from(comment, false, Collections.emptyList());
    }

    @Transactional
    public void deleteComment(UUID postId, UUID commentId, UUID userId) {
        PostComment comment = postCommentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ResourceNotFoundException("Yorum bulunamadı"));
        CommunityPost post = comment.getPost();

        boolean isCommentOwner = comment.getUser().getId().equals(userId);
        boolean isPostOwner = post.getUser().getId().equals(userId);
        if (!isCommentOwner && !isPostOwner)
            throw new UnauthorizedException("You do not have permission to delete this comment");

        long replyCount = postCommentRepository.countByParentId(commentId);
        communityPostRepository.decrementCommentsCount(postId, (int) (1 + replyCount));
        postCommentRepository.delete(comment);
    }

    // ── Comment Like / Unlike ─────────────────────────────────────────────────

    @Transactional
    public void likeComment(UUID commentId, UUID userId) {
        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) return;
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Yorum bulunamadı"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        try {
            commentLikeRepository.saveAndFlush(CommentLike.builder().comment(comment).user(user).build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate comment like ignored: commentId={} userId={}", commentId, userId);
            return;
        }
        postCommentRepository.incrementLikesCount(commentId);
    }

    @Transactional
    public void unlikeComment(UUID commentId, UUID userId) {
        commentLikeRepository.findByCommentIdAndUserId(commentId, userId).ifPresent(like -> {
            commentLikeRepository.delete(like);
            postCommentRepository.decrementLikesCount(commentId);
        });
    }

    // ── Follow / Unfollow ─────────────────────────────────────────────────────

    @Transactional
    public void followUser(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId))
            throw new IllegalArgumentException("Cannot follow yourself");
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) return;
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new ResourceNotFoundException("Takip edilecek kullanıcı bulunamadı"));
        try {
            followRepository.saveAndFlush(Follow.builder().follower(follower).following(following).build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate follow ignored: followerId={} followingId={}", followerId, followingId);
        }
    }

    @Transactional
    public void unfollowUser(UUID followerId, UUID followingId) {
        followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .ifPresent(followRepository::delete);
    }

    private String normalizeBadgeKey(String badgeKey) {
        if (badgeKey == null || badgeKey.isBlank()) {
            return null;
        }
        return badgeKey.trim().toUpperCase(Locale.ROOT);
    }

}
