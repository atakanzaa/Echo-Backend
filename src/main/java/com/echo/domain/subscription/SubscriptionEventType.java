package com.echo.domain.subscription;

/**
 * Internal subscription lifecycle event types stored in {@link SubscriptionEvent#getEventType()}.
 * Persisted as the enum {@link #name()} string so the column stays varchar-compatible
 * and historical rows remain readable without a migration.
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
