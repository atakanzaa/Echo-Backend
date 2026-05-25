package com.echo.domain.subscription;

/**
 * Audit event types persisted to {@code subscription_events.event_type}.
 * Names map directly to the stored string values.
 */
public enum SubscriptionEventType {
    SUBSCRIBED,
    RENEWED,
    RESTORED,
    BILLING_RETRY,
    EXPIRED,
    REVOKED,
    CANCELLED
}
