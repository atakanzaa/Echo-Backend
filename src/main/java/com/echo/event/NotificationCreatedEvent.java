package com.echo.event;

import java.util.UUID;

public record NotificationCreatedEvent(UUID notificationId) {
}
