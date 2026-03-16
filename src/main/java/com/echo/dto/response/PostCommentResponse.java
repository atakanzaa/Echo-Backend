package com.echo.dto.response;

import com.echo.domain.community.PostComment;

import java.util.List;
import java.util.UUID;

public record PostCommentResponse(
        UUID   id,
        String userId,
        String displayName,
        String content,
        UUID   parentId,
        int    likesCount,
        boolean isLikedByMe,
        List<PostCommentResponse> replies,
        String createdAt
) {
    public static PostCommentResponse from(PostComment comment, boolean likedByMe, List<PostCommentResponse> replies) {
        return new PostCommentResponse(
                comment.getId(),
                comment.getUser().getId().toString(),
                comment.getUser().getDisplayName(),
                comment.getContent(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getLikesCount(),
                likedByMe,
                replies,
                comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : java.time.OffsetDateTime.now().toString()
        );
    }
}
