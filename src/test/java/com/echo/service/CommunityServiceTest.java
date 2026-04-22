package com.echo.service;

import com.echo.domain.community.CommunityPost;
import com.echo.domain.community.PostComment;
import com.echo.domain.community.PostLike;
import com.echo.domain.user.User;
import com.echo.event.PostLikedEvent;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.CommentLikeRepository;
import com.echo.repository.CommunityPostRepository;
import com.echo.repository.FollowRepository;
import com.echo.repository.PostCommentRepository;
import com.echo.repository.PostLikeRepository;
import com.echo.repository.UserAchievementRepository;
import com.echo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock CommunityPostRepository communityPostRepository;
    @Mock PostLikeRepository postLikeRepository;
    @Mock PostCommentRepository postCommentRepository;
    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;
    @Mock CommentLikeRepository commentLikeRepository;
    @Mock StorageService storageService;
    @Mock UserAchievementRepository userAchievementRepository;
    @Mock AchievementService achievementService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks CommunityService communityService;

    @Test
    void deleteCommentRejectsCommentOutsidePathPost() {
        UUID pathPostId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(postCommentRepository.findByIdAndPostId(commentId, pathPostId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> communityService.deleteComment(pathPostId, commentId, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(communityPostRepository, never()).decrementCommentsCount(any(), anyInt());
        verify(postCommentRepository, never()).delete(any());
    }

    @Test
    void likePostDuplicateRaceDoesNotIncrementOrPublish() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).build();
        User user = User.builder().id(userId).build();
        CommunityPost post = CommunityPost.builder().id(postId).user(owner).build();

        given(postLikeRepository.existsByPostIdAndUserId(postId, userId)).willReturn(false);
        given(communityPostRepository.findById(postId)).willReturn(Optional.of(post));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(postLikeRepository.saveAndFlush(any(PostLike.class)))
                .willThrow(new DataIntegrityViolationException("duplicate like"));

        communityService.likePost(postId, userId);

        verify(communityPostRepository, never()).incrementLikesCount(postId);
        verify(eventPublisher, never()).publishEvent(any(PostLikedEvent.class));
    }

    @Test
    void getFeedChecksLikedStateForCurrentPageOnly() {
        UUID userId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).displayName("Owner").build();
        CommunityPost first = CommunityPost.builder().id(UUID.randomUUID()).user(owner).content("a").build();
        CommunityPost second = CommunityPost.builder().id(UUID.randomUUID()).user(owner).content("b").build();

        given(communityPostRepository.findByPublicPostTrueOrderByCreatedAtDesc(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(first, second)));
        given(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(eq(userId), anySet()))
                .willReturn(Set.of(first.getId()));

        communityService.getFeed(userId, "global", 0, 20);

        verify(postLikeRepository).findLikedPostIdsByUserIdAndPostIds(
                eq(userId),
                eq(Set.of(first.getId(), second.getId()))
        );
        verify(postLikeRepository, never()).findLikedPostIdsByUserId(userId);
    }
}
