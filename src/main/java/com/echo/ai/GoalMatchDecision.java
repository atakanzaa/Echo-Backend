package com.echo.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record GoalMatchDecision(
        @JsonProperty("goal_id")
        UUID goalId,
        double confidence,
        @JsonProperty("should_auto_complete")
        boolean shouldAutoComplete,
        @JsonProperty("needs_confirmation")
        boolean needsConfirmation,
        String reason
) {}
