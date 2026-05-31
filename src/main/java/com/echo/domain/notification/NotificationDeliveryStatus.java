package com.echo.domain.notification;

public enum NotificationDeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    REJECTED,
    SKIPPED_DISABLED,
    SKIPPED_QUIET_HOURS,
    SKIPPED_RATE_LIMITED,
    SKIPPED_NO_TOKEN
}
