package com.echo.dto.response;

import java.util.List;

public record AchievementsResponse(
        int    currentStreak,
        int    longestStreak,
        int    totalEntries,
        int    totalWords,
        double insightScore,
        List<BadgeItem> badges,
        String level,
        String levelMessage
) {
    public record BadgeItem(
            String  id,
            String  key,
            String  title,
            String  emoji,
            boolean isEarned,
            String  earnedAt
    ) {}
}
