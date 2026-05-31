package com.echo.service.notification;

import com.echo.domain.notification.NotificationType;

import java.util.Map;
import java.util.UUID;

public record ApnsPushPayload(
        UUID notificationId,
        NotificationType type,
        String title,
        String body,
        String threadId,
        Map<String, String> data
) {
}
