package com.echo.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A goal detected by the AI from a journal entry.
 * Example: "Go to the gym tomorrow morning" → title="Go to the gym", timeframe="tomorrow morning"
 */
public record AIGoal(
        String title,

        String timeframe,

        @JsonProperty("goal_type")
        String goalType,

        Double confidence,

        String reason,

        @JsonProperty("source_quote")
        String sourceQuote
) {}
