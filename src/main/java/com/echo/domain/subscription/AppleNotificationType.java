package com.echo.domain.subscription;

import java.util.Locale;
import java.util.Map;

/**
 * Apple App Store Server Notification V2 types we explicitly act on.
 * Centralised so the SubscriptionService switch isn't littered with magic strings,
 * and so unknown types degrade gracefully through {@link #fromString(String)}.
 */
public enum AppleNotificationType {
    SUBSCRIBED,
    INITIAL_BUY,
    DID_RENEW,
    DID_FAIL_TO_RENEW,
    GRACE_PERIOD_EXPIRED,
    EXPIRED,
    REVOKE,
    REVOKED,
    DID_CHANGE_RENEWAL_STATUS,
    UNKNOWN;

    private static final Map<String, AppleNotificationType> INDEX = Map.of(
            "SUBSCRIBED", SUBSCRIBED,
            "INITIAL_BUY", INITIAL_BUY,
            "DID_RENEW", DID_RENEW,
            "DID_FAIL_TO_RENEW", DID_FAIL_TO_RENEW,
            "GRACE_PERIOD_EXPIRED", GRACE_PERIOD_EXPIRED,
            "EXPIRED", EXPIRED,
            "REVOKE", REVOKE,
            "REVOKED", REVOKED,
            "DID_CHANGE_RENEWAL_STATUS", DID_CHANGE_RENEWAL_STATUS
    );

    public static AppleNotificationType fromString(String value) {
        if (value == null) return UNKNOWN;
        return INDEX.getOrDefault(value.trim().toUpperCase(Locale.ROOT), UNKNOWN);
    }
}
