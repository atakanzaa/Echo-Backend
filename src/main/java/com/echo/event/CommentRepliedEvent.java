package com.echo.event;

import java.util.UUID;

public record CommentRepliedEvent(
        UUID actorUserId,
        UUID commentOwnerUserId,
        UUID postId,
        UUID commentId,
        boolean anonymousPost
) {}
