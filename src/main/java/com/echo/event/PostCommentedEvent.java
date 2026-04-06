package com.echo.event;

import java.util.UUID;

public record PostCommentedEvent(
        UUID actorUserId,
        UUID postOwnerUserId,
        UUID postId,
        UUID commentId,
        boolean anonymousPost
) {}
