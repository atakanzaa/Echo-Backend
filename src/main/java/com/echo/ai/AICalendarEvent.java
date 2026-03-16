package com.echo.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI'nın günlük girişinden tespit ettiği gelecek etkinlik/plan.
 * Örnek: "Yarın sabah spor salonu" → title="Gym session", scheduledFor="tomorrow morning"
 * Not: scheduledFor doğal dil formatında — kesin ISO tarih değil.
 */
public record AICalendarEvent(
        String title,

        @JsonProperty("scheduled_for")
        String scheduledFor,

        @JsonProperty("event_type")
        String eventType    // "health" | "work" | "social" | "personal"
) {}
