package com.echo.ai;

import java.util.List;

/**
 * Synthesis engine girdi verisi — kullanıcının tüm veri kaynaklarını birleştirir.
 * Token limitleri: entries max 30, coachExchanges max 20, summary max 200 char.
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
        String previousPeriodTrend
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
