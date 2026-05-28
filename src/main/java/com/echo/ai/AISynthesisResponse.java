package com.echo.ai;

import java.util.List;

/**
 * Synthesis engine output — feeds Summary, Insights, Achievements and the Memory Layer.
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
