package com.echo.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CalendarMonthResponse(
        int  year,
        int  month,
        List<CalendarDay> days,
        MonthSummary monthSummary
) {
    public record CalendarDay(
            int    day,
            String moodLabel,
            String entryId,
            double moodScore,
            String moodCategory,
            int    entryCount,
            String dominantEmotion,
            boolean hasCoachSession
    ) {}

    public record MonthSummary(
            BigDecimal averageMood,
            int totalEntries,
            int streakDays
    ) {}
}
