package com.echo.dto.response;

public record AchievementDetailResponse(
        String badgeKey,
        String title,
        String emoji,
        String description,
        String criteriaDescription,
        boolean isEarned,
        String earnedAt,
        double progress,
        String shareText
) {}
