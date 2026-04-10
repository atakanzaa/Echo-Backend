package com.echo.ai;

import java.util.List;

public record GoalMatchVerificationRequest(
        String utterance,
        String language,
        List<GoalMatchCandidate> candidates
) {}
