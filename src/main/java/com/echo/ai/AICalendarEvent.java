package com.echo.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A future event/plan detected by the AI from a journal entry.
 * Example: "Gym tomorrow morning" → title="Gym session", scheduledFor="tomorrow morning"
 * Note: scheduledFor is in natural-language form — not a precise ISO date.
 */
public record AICalendarEvent(
        String title,

        @JsonProperty("scheduled_for")
        String scheduledFor,

        @JsonProperty("event_type")
        String eventType    // "health" | "work" | "social" | "personal"
) {}
