package com.echo.event;

import java.util.UUID;

public record AchievementProgressEvent(
        UUID userId,
        String badgeKey,
        String badgeTitle,
        int remaining
) {}
