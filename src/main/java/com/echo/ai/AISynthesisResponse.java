package com.echo.ai;

import java.util.List;

/**
 * Synthesis engine çıktı verisi — Summary, Insights, Achievements ve Memory Layer'ı besler.
 */
public record AISynthesisResponse(
        String narrativeSummary,
        List<Suggestion> suggestions,
        String emotionalAssessment,
        int growthScore,
        String growthLabel,
        String growthMessage,
        List<String> weeklyThemes,
        String coachInsight,
        ProfileUpdate profileUpdate,
        String rawJson
) {
    public record Suggestion(String title, String body, String icon) {}

    public record ProfileUpdate(
            String userProfile,
            String emotionalPatterns,
            String valuesStrengths,
            String growthTrajectory
    ) {}
}
