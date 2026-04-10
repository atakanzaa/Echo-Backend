package com.echo.domain.subscription;

public enum SubscriptionStatus {
    ACTIVE,
    GRACE_PERIOD,
    BILLING_RETRY,
    EXPIRED,
    REVOKED,
    CANCELLED;

    public boolean grantsAccess() {
        return this == ACTIVE || this == GRACE_PERIOD || this == BILLING_RETRY;
    }
}
