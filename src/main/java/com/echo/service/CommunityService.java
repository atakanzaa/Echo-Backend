package com.echo.service;

import com.echo.domain.community.*;
import com.echo.domain.user.User;
import com.echo.dto.request.CreateCommentRequest;
import com.echo.dto.request.CreatePostRequest;
import com.echo.dto.response.CommunityPostResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.dto.response.PostCommentResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.exception.UnauthorizedException;
import com.echo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityPostRepository communityPostRepository;
    private final PostLikeRepository      postLikeRepository;
    private final PostCommentRepository   postCommentRepository;
    private final UserRepository          userRepository;
    private final FollowRepository        followRepository;
    private final CommentLikeRepository   commentLikeRepository;

    private static final String IMAGE_UPLOAD_DIR = "/tmp/echo-uploads/images";

    // ── Feed ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<CommunityPostResponse> getFeed(UUID userId, String tab, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<CommunityPost> posts = "following".equalsIgnoreCase(tab)
                ? getFollowingFeed(userId, pageable)
                : communityPostRepository.findByPublicPostTrueOrderByCreatedAtDesc(pageable);

        Set<UUID> likedPostIds = postLikeRepository.findAll().stream()
                .filter(l -> l.getUser().getId().equals(userId))
                .map(l -> l.getPost().getId())
                .collect(Collectors.toSet());

        return PagedResponse.from(posts, p -> CommunityPostResponse.from(p, likedPostIds.contains(p.getId())));
    }

    private Page<CommunityPost> getFollowingFeed(UUID userId, Pageable pageable) {
        List<UUID> followingIds = followRepository.findByFollowerId(userId).stream()
                .map(f -> f.getFollowing().getId())
                .collect(Collectors.toList());
        if (followingIds.isEmpty()) return Page.empty(pageable);
        return communityPostRepository.findByPublicPostTrueAndUserIdInOrderByCreatedAtDesc(followingIds, pageable);
    }

    // ── Single Post ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CommunityPostResponse getPost(UUID postId, UUID userId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Gönderi bulunamadı"));
        boolean liked = postLikeRepository.existsByPostIdAndUserId(postId, userId);
        return CommunityPostResponse.from(post, liked);
    }

    // ── Create Post ───────────────────────────────────────────────────────────

    @Transactional
    public CommunityPostResponse createPost(UUID userId, CreatePostRequest request, MultipartFile imageFile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));

        String imageUrl = null;
        String contentType = request.contentType() != null ? request.contentType() : "text";

        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = saveImage(imageFile);
            contentType = "image";
        }

        if (imageUrl == null && (request.content() == null || request.content().isBlank())) {
            throw new IllegalArgumentException("Text post must have content");
        }

        CommunityPost post = CommunityPost.builder()
                .user(user)
                .content(request.content())
                .contentType(contentType)
                .emoji(request.emoji())
                .anonymous(request.isAnonymous())
                .imageUrl(imageUrl)
                .build();
        return CommunityPostResponse.from(communityPostRepository.save(post), false);
    }

    // ── Delete Post ───────────────────────────────────────────────────────────

    @Transactional
    public void deletePost(UUID postId, UUID userId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Gönderi bulunamadı"));
        if (!post.getUser().getId().equals(userId))
            throw new UnauthorizedException("Bu gönderiyi silme yetkiniz yok");
        communityPostRepository.delete(post);
    }

    // ── Post Like / Unlike ────────────────────────────────────────────────────

    @Transactional
    public void likePost(UUID postId, UUID userId) {
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) return;
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Gönderi bulunamadı"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));
        postLikeRepository.save(PostLike.builder().post(post).user(user).build());
        post.setLikesCount(post.getLikesCount() + 1);
        communityPostRepository.save(post);
    }

    @Transactional
    public void unlikePost(UUID postId, UUID userId) {
        postLikeRepository.findByPostIdAndUserId(postId, userId).ifPresent(like -> {
            postLikeRepository.delete(like);
            communityPostRepository.findById(postId).ifPresent(post -> {
                post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
                communityPostRepository.save(post);
            });
        });
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<PostCommentResponse> getComments(UUID postId, UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        var topLevel = postCommentRepository.findByPostIdAndParentIsNullOrderByCreatedAtAsc(postId, pageable);

        return PagedResponse.from(topLevel, comment -> {
            boolean likedByMe = commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), userId);
            List<PostCommentResponse> replies = postCommentRepository
                    .findByParentIdOrderByCreatedAtAsc(comment.getId()).stream()
                    .map(reply -> {
                        boolean replyLiked = commentLikeRepository.existsByCommentIdAndUserId(reply.getId(), userId);
                        return PostCommentResponse.from(reply, replyLiked, Collections.emptyList());
                    })
                    .collect(Collectors.toList());
            return PostCommentResponse.from(comment, likedByMe, replies);
        });
    }

    @Transactional
    public PostCommentResponse createComment(UUID postId, UUID userId, CreateCommentRequest request) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Gönderi bulunamadı"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));

        PostComment.PostCommentBuilder builder = PostComment.builder()
                .post(post).user(user).content(request.content());

        if (request.parentId() != null) {
            PostComment parent = postCommentRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent yorum bulunamadı"));
            if (!parent.getPost().getId().equals(postId))
                throw new IllegalArgumentException("Parent comment does not belong to this post");
            if (parent.getParent() != null)
                throw new IllegalArgumentException("Cannot reply to a reply (max 1 level depth)");
            builder.parent(parent);
        }

        PostComment comment = postCommentRepository.save(builder.build());
        post.setCommentsCount(post.getCommentsCount() + 1);
        communityPostRepository.save(post);
        return PostCommentResponse.from(comment, false, Collections.emptyList());
    }

    @Transactional
    public void deleteComment(UUID postId, UUID commentId, UUID userId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Yorum bulunamadı"));
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Gönderi bulunamadı"));

        boolean isCommentOwner = comment.getUser().getId().equals(userId);
        boolean isPostOwner = post.getUser().getId().equals(userId);
        if (!isCommentOwner && !isPostOwner)
            throw new UnauthorizedException("Bu yorumu silme yetkiniz yok");

        long replyCount = postCommentRepository.countByParentId(commentId);
        post.setCommentsCount(Math.max(0, post.getCommentsCount() - (int) (1 + replyCount)));
        communityPostRepository.save(post);
        postCommentRepository.delete(comment);
    }

    // ── Comment Like / Unlike ─────────────────────────────────────────────────

    @Transactional
    public void likeComment(UUID commentId, UUID userId) {
        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) return;
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Yorum bulunamadı"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));
        commentLikeRepository.save(CommentLike.builder().comment(comment).user(user).build());
        comment.setLikesCount(comment.getLikesCount() + 1);
        postCommentRepository.save(comment);
    }

    @Transactional
    public void unlikeComment(UUID commentId, UUID userId) {
        commentLikeRepository.findByCommentIdAndUserId(commentId, userId).ifPresent(like -> {
            commentLikeRepository.delete(like);
            postCommentRepository.findById(commentId).ifPresent(comment -> {
                comment.setLikesCount(Math.max(0, comment.getLikesCount() - 1));
                postCommentRepository.save(comment);
            });
        });
    }

    // ── Follow / Unfollow ─────────────────────────────────────────────────────

    @Transactional
    public void followUser(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId))
            throw new IllegalArgumentException("Cannot follow yourself");
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) return;
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new ResourceNotFoundException("Takip edilecek kullanıcı bulunamadı"));
        followRepository.save(Follow.builder().follower(follower).following(following).build());
    }

    @Transactional
    public void unfollowUser(UUID followerId, UUID followingId) {
        followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .ifPresent(followRepository::delete);
    }

    // ── Image Storage ─────────────────────────────────────────────────────────

    private String saveImage(MultipartFile file) {
        try {
            Path dir = Paths.get(IMAGE_UPLOAD_DIR);
            Files.createDirectories(dir);
            String ext = getExtension(file.getOriginalFilename(), "jpg");
            String filename = UUID.randomUUID() + "." + ext;
            Files.write(dir.resolve(filename), file.getBytes());
            return "/uploads/images/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Image could not be saved", e);
        }
    }

    private String getExtension(String filename, String defaultExt) {
        if (filename == null || !filename.contains(".")) return defaultExt;
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
