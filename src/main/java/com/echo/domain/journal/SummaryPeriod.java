package com.echo.domain.journal;

import java.util.Arrays;

public enum SummaryPeriod {
    WEEK(7), TWO_WEEKS(14), MONTH(30),
    QUARTER(90), HALF_YEAR(180), YEAR(365);

    private final int days;

    SummaryPeriod(int days) { this.days = days; }

    public int getDays() { return days; }

    public static SummaryPeriod fromDays(int days) {
        return Arrays.stream(values())
                .filter(p -> p.days == days)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Geçersiz periyot: " + days + ". Geçerli: 7, 14, 30, 90, 180, 365"));
    }
}
