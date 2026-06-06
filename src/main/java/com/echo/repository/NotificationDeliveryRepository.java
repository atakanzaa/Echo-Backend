package com.echo.repository;

import com.echo.domain.notification.NotificationDelivery;
import com.echo.domain.notification.NotificationDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    boolean existsByNotificationIdAndPushTokenId(UUID notificationId, UUID pushTokenId);

    long countByStatusAndCreatedAtAfter(NotificationDeliveryStatus status, OffsetDateTime since);

    long countByStatusInAndCreatedAtAfter(Collection<NotificationDeliveryStatus> statuses, OffsetDateTime since);

    long countByStatus(NotificationDeliveryStatus status);
}
