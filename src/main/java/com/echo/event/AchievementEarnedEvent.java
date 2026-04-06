package com.echo.event;

import java.util.UUID;

public record AchievementEarnedEvent(
        UUID userId,
        String badgeKey,
        String badgeTitle,
        String badgeEmoji
) {}
