package com.echo.event;

import java.util.UUID;

public record PostLikedEvent(
        UUID actorUserId,
        UUID postOwnerUserId,
        UUID postId
) {}
