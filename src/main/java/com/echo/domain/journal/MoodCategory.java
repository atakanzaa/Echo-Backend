package com.echo.domain.journal;

/**
 * Score-range based mood category — 4 buckets covering 0.0-1.0 scale.
 * Displayed as 0-10 to the user.
 *
 * 0.00 – 0.25 → STRESS    (0 – 2.5)
 * 0.25 – 0.50 → NEUTRAL   (2.5 – 5.0)
 * 0.50 – 0.75 → CALM      (5.0 – 7.5)
 * 0.75 – 1.00 → MOTIVATED (7.5 – 10.0)
 */
public enum MoodCategory {
    STRESS, NEUTRAL, CALM, MOTIVATED;

    public static MoodCategory fromScore(double score) {
        if (score < 0.25) return STRESS;
        if (score < 0.50) return NEUTRAL;
        if (score < 0.75) return CALM;
        return MOTIVATED;
    }
}
