package com.echo.domain.subscription;

public final class SubscriptionEventType {
    public static final String SUBSCRIBED = "SUBSCRIBED";
    public static final String RENEWED = "RENEWED";
    public static final String RESTORED = "RESTORED";
    public static final String BILLING_RETRY = "BILLING_RETRY";
    public static final String EXPIRED = "EXPIRED";
    public static final String REVOKED = "REVOKED";
    public static final String CANCELLED = "CANCELLED";

    private SubscriptionEventType() {}
}
