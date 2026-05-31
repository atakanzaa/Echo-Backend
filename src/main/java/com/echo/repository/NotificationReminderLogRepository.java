package com.echo.repository;

import com.echo.domain.notification.NotificationReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface NotificationReminderLogRepository extends JpaRepository<NotificationReminderLog, UUID> {
    boolean existsByUserIdAndReminderTypeAndLocalDate(UUID userId, String reminderType, LocalDate localDate);

    @Modifying
    @Query("DELETE FROM NotificationReminderLog l WHERE l.localDate < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDate cutoff);
}
