package com.echo.dto.response;

import java.util.List;

public record CalendarMonthResponse(
        int  year,
        int  month,
        List<CalendarDay> days
) {
    public record CalendarDay(
            int    day,
            String moodLabel,
            String entryId,
            double moodScore,
            String moodCategory
    ) {}
}
