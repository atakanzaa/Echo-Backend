package com.echo.dto.response;

import com.echo.domain.notification.Notification;

import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String groupKey,
        String targetType,
        UUID targetId,
        boolean isRead,
        String createdAt,
        String readAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getGroupKey(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.isRead(),
                notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : null,
                notification.getReadAt() != null ? notification.getReadAt().toString() : null
        );
    }
}
