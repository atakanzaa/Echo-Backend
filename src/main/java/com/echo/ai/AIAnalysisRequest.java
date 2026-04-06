package com.echo.ai;

public record AIAnalysisRequest(
        String transcript,
        String userTimezone,
        String language
) {}
