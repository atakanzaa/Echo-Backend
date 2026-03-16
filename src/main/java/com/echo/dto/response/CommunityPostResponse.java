package com.echo.dto.response;

import com.echo.domain.community.CommunityPost;

import java.util.UUID;

public record CommunityPostResponse(
        UUID    id,
        String  userId,
        String  displayName,
        String  content,
        String  contentType,
        String  audioUrl,
        Integer audioDuration,
        String  imageUrl,
        String  emoji,
        boolean isAnonymous,
        int     likesCount,
        int     commentsCount,
        boolean isLikedByMe,
        String  createdAt
) {
    public static CommunityPostResponse from(CommunityPost post, boolean likedByMe) {
        String name = post.isAnonymous() ? "Anonymous" :
                (post.getUser().getDisplayName() != null ? post.getUser().getDisplayName() : "User");
        return new CommunityPostResponse(
                post.getId(),
                post.isAnonymous() ? null : post.getUser().getId().toString(),
                name,
                post.getContent(),
                post.getContentType(),
                null,
                post.getAudioDuration(),
                post.getImageUrl(),
                post.getEmoji(),
                post.isAnonymous(),
                post.getLikesCount(),
                post.getCommentsCount(),
                likedByMe,
                post.getCreatedAt() != null ? post.getCreatedAt().toString() : java.time.OffsetDateTime.now().toString()
        );
    }
}
