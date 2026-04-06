package com.echo.repository;

import com.echo.domain.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByEventId(String eventId);

    Optional<Notification> findFirstByUserIdAndGroupKeyAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID userId, String groupKey, OffsetDateTime after);

    @Modifying
    @Query("""
           UPDATE Notification n
           SET n.read = true, n.readAt = :readAt
           WHERE n.id = :id AND n.user.id = :userId AND n.read = false
           """)
    int markAsRead(@Param("userId") UUID userId,
                   @Param("id") UUID id,
                   @Param("readAt") OffsetDateTime readAt);

    @Modifying
    @Query("""
           UPDATE Notification n
           SET n.read = true, n.readAt = :readAt
           WHERE n.user.id = :userId AND n.read = false
           """)
    int markAllAsRead(@Param("userId") UUID userId,
                      @Param("readAt") OffsetDateTime readAt);
}
