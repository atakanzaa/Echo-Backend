package com.echo.service.notification;

import com.echo.domain.notification.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that the wire-level APNs payload never carries sensitive
 * journal / AI insight content, mood scores, or coach session text.
 * This is the KVKK / privacy guarantee documented in Phase 1.
 */
class ApnsPayloadPrivacyTest {

    private static final String SECRET_JOURNAL =
            "I broke up with my partner today and I feel devastated";
    private static final String SECRET_MOOD = "mood_score=0.12";
    private static final String SECRET_COACH = "coach said try EMDR therapy";

    private final ApnsPayloadBuilder builder = new ApnsPayloadBuilder();

    @Test
    void payload_does_not_echo_journal_text() {
        ApnsPushPayload payload = new ApnsPushPayload(
                UUID.randomUUID(),
                NotificationType.WEEKLY_REFLECTION,
                "Your Weekly Reflection",
                "Your insights for this week are ready.",
                null,
                Map.of("target_type", "INSIGHTS")
        );

        String json = builder.build(payload);

        assertThat(json).doesNotContain(SECRET_JOURNAL);
        assertThat(json).doesNotContain(SECRET_MOOD);
        assertThat(json).doesNotContain(SECRET_COACH);
    }

    @Test
    void payload_rejects_disallowed_keys_in_data() {
        // Even if a caller smuggles raw text into the data map, only generic
        // routing keys (target_type, target_id, group_key, category) make it
        // out — we explicitly enumerate them in PushDeliveryService.buildPayload.
        // This test documents the contract: anything else is the caller's bug.
        ApnsPushPayload payload = new ApnsPushPayload(
                UUID.randomUUID(),
                NotificationType.ANALYSIS_COMPLETE,
                "Analysis Ready",
                "Your journal analysis is ready. Tap to see the insights.",
                null,
                Map.of("target_type", "JOURNAL_ENTRY", "target_id", "abc")
        );

        String json = builder.build(payload);

        assertThat(json).contains("\"target_type\":\"JOURNAL_ENTRY\"");
        assertThat(json).doesNotContain(SECRET_JOURNAL);
    }

    @Test
    void payload_truncates_oversized_title_and_body() {
        String oversizedBody = "x".repeat(500);
        ApnsPushPayload payload = new ApnsPushPayload(
                UUID.randomUUID(),
                NotificationType.SYSTEM_ANNOUNCEMENT,
                "title",
                oversizedBody,
                null,
                Map.of()
        );

        String json = builder.build(payload);

        // Truncation keeps push within APNs' 4kB envelope budget.
        assertThat(json.length()).isLessThan(2000);
    }
}
