package com.echo.dto.response;

import java.time.LocalDate;
import java.util.List;

public record OnThisDayResponse(List<OnThisDayEntry> entries) {

    public record OnThisDayEntry(
            String id,
            LocalDate date,
            String summary,
            String moodLabel,
            double moodScore,
            int yearsAgo
    ) {}
}
