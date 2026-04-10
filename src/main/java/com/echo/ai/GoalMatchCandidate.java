package com.echo.ai;

import java.util.UUID;

public record GoalMatchCandidate(
        UUID goalId,
        String title,
        String timeframe,
        String goalType
) {}
