package com.echo.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI'nın günlük girişinden tespit ettiği hedef.
 * Örnek: "Yarın sabah spor salonuna git" → title="Go to the gym", timeframe="tomorrow morning"
 */
public record AIGoal(
        String title,

        String timeframe,

        @JsonProperty("goal_type")
        String goalType
) {}
