package com.echo.dto.response;

import java.util.List;

public record InsightsPeriodEligibilityResponse(
        List<PeriodOption> periods,
        int totalEntries,
        int totalDistinctDays
) {
    public record PeriodOption(
            int days,
            boolean unlocked,
            int requiredEntries,
            int currentEntries,
            int requiredDistinctDays,
            int currentDistinctDays
    ) {}
}
