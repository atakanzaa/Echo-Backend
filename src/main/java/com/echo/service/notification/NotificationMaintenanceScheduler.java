package com.echo.service.notification;

import com.echo.repository.NotificationReminderLogRepository;
import com.echo.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Periodic maintenance: drops APNs tokens that have been inactive for 90+ days
 * (KVKK / storage hygiene) and trims the reminder log so it doesn't grow forever.
 * Runs daily at 03:30 UTC, off the main reminder window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationMaintenanceScheduler {

    private static final int TOKEN_RETENTION_DAYS = 90;
    private static final int REMINDER_LOG_RETENTION_DAYS = 60;

    private final PushTokenRepository pushTokenRepository;
    private final NotificationReminderLogRepository reminderLogRepository;

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public void runDailyCleanup() {
        OffsetDateTime tokenCutoff = OffsetDateTime.now().minusDays(TOKEN_RETENTION_DAYS);
        int tokensDeleted = pushTokenRepository.deleteInactiveOlderThan(tokenCutoff);

        LocalDate reminderCutoff = LocalDate.now().minusDays(REMINDER_LOG_RETENTION_DAYS);
        int remindersDeleted = reminderLogRepository.deleteOlderThan(reminderCutoff);

        if (tokensDeleted > 0 || remindersDeleted > 0) {
            log.info("Notification maintenance: pushTokensDeleted={} reminderLogsDeleted={}",
                    tokensDeleted, remindersDeleted);
        }
    }
}
