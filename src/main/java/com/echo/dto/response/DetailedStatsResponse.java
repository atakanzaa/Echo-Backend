package com.echo.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DetailedStatsResponse(
        List<Integer> weeklyConsistency,
        double averageEntriesPerWeek,
        String mostActiveDay,
        int mostActiveHour,
        List<String> topTopicsThisMonth,
        List<String> topEmotionsThisMonth,
        BigDecimal moodTrendVsLastMonth,
        long totalWords,
        int averageEntryLength
) {}
