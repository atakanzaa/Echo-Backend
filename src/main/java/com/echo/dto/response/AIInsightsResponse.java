package com.echo.dto.response;

import java.util.List;

public record AIInsightsResponse(
        int    period,
        double moodScore,
        String moodTrend,
        List<Theme>      themes,
        List<Suggestion> suggestions,
        double emotionalTrend
) {
    public record Theme(String tag, int count, boolean trending) {}
    public record Suggestion(String title, String body, String icon) {}
}
