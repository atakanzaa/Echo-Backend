package com.echo.ai;

import java.util.List;

/**
 * Synthesis engine input — combines all of the user's data sources.
 * Token limits: entries max 30, coachExchanges max 20, summary max 200 chars.
 */
public record AISynthesisRequest(
        int periodDays,
        List<EntrySummary> entries,
        List<CoachExchange> coachExchanges,
        List<String> activeGoals,
        int completedGoalCount,
        int currentStreak,
        int totalEntries,
        String userProfile,
        String previousPeriodTrend,
        String language
) {
    public record EntrySummary(
            String date,
            double moodScore,
            String moodLabel,
            List<String> topics,
            List<String> emotions,
            String energyLevel,
            String summary
    ) {}

    public record CoachExchange(
            String date,
            String userMessage,
            String aiResponse
    ) {}
}
