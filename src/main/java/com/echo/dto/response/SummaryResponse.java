package com.echo.dto.response;

import java.util.List;
import java.util.Map;

public record SummaryResponse(
        String startDate,
        String endDate,
        int    periodDays,
        int    recordedDays,
        double coveragePercent,
        double averageMoodScore,
        double highestMoodScore,
        double lowestMoodScore,
        String moodTrend,
        List<String>      dominantTopics,
        List<String>      topEmotions,
        Map<String, Integer> energyDistribution,
        Map<String, Double> moodCategoryDistribution,
        String bestDay,
        String worstDay,
        String narrativeSummary,
        List<DailyMoodScore> dailyMoodScores
) {
    /** Günlük bazda mood verisi — Insights weekly chart ve emotion distribution için */
    public record DailyMoodScore(String date, double moodScore, String moodLabel, String moodCategory) {}

    public static SummaryResponse empty(java.time.LocalDate startDate,
                                        java.time.LocalDate endDate,
                                        int periodDays) {
        return new SummaryResponse(
                startDate.toString(), endDate.toString(), periodDays,
                0, 0.0, 0.0, 0.0, 0.0, "stable",
                List.of(), List.of(), Map.of(),
                Map.of("STRESS", 0.0, "NEUTRAL", 0.0, "CALM", 0.0, "MOTIVATED", 0.0),
                null, null,
                "Bu dönemde henüz günlük girişi yok.",
                List.of()
        );
    }
}
