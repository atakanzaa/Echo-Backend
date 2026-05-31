package com.echo.repository;

import com.echo.domain.notification.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    boolean existsByNotificationIdAndPushTokenId(UUID notificationId, UUID pushTokenId);
}
