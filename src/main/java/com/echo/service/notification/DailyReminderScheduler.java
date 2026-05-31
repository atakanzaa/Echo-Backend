package com.echo.service.notification;

import com.echo.domain.notification.NotificationPreference;
import com.echo.domain.notification.NotificationReminderLog;
import com.echo.domain.notification.NotificationType;
import com.echo.domain.user.User;
import com.echo.repository.NotificationReminderLogRepository;
import com.echo.repository.UserRepository;
import com.echo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Runs every hour on the hour. For each user whose `preferred_local_hour`
 * matches the hour-of-day in their timezone, evaluates which reminder sub-flow
 * applies and emits at most one push per (user, sub-flow, local-date).
 *
 * Sub-flows, evaluated in order of priority:
 *   1. FIRST_JOURNAL — total_entries == 0, account age <= 7 days
 *   2. INACTIVITY    — last_entry_date older than 3 days, account age > 7 days
 *   3. STREAK        — current streak >= 3 AND no entry today
 *   4. DAILY         — wrote an entry within the last 24h (active habit)
 *
 * Idempotency: `notification_reminder_log(user_id, reminder_type, local_date)`
 * is unique. A duplicate insert throws DataIntegrityViolationException and we
 * skip the emit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReminderScheduler {

    private static final int FIRST_JOURNAL_GRACE_DAYS = 7;
    private static final int INACTIVITY_DAYS_THRESHOLD = 3;
    private static final int STREAK_REMINDER_MIN = 3;

    private final UserRepository userRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationReminderLogRepository reminderLogRepository;
    private final NotificationService notificationService;
    private final NotificationTemplateService templateService;

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @Transactional
    public void runHourly() {
        int utcHour = ZonedDateTime.now(ZoneId.of("UTC")).getHour();
        log.info("Daily reminder scheduler tick utcHour={}", utcHour);

        for (int preferredHour = 0; preferredHour < 24; preferredHour++) {
            List<User> candidates = userRepository.findUsersForReminderHour(preferredHour);
            if (candidates.isEmpty()) continue;

            int processed = 0;
            int emitted = 0;
            for (User user : candidates) {
                NotificationPreference pref = preferenceService.getOrCreate(user.getId());
                if (!pref.isCategoryEnabled(com.echo.domain.notification.NotificationCategory.DAILY_REMINDERS)) {
                    continue;
                }
                ZoneId zone = resolveZone(pref, user);
                ZonedDateTime nowLocal = ZonedDateTime.now(zone);
                if (nowLocal.getHour() != preferredHour) continue;

                processed++;
                if (tryEmit(user, nowLocal.toLocalDate())) emitted++;
            }
            if (processed > 0) {
                log.info("Daily reminders preferredHour={} candidates={} processed={} emitted={}",
                        preferredHour, candidates.size(), processed, emitted);
            }
        }
    }

    private boolean tryEmit(User user, LocalDate localDate) {
        ReminderKind kind = decideKind(user, localDate);
        if (kind == null) return false;

        if (reminderLogRepository.existsByUserIdAndReminderTypeAndLocalDate(
                user.getId(), kind.type.name(), localDate)) {
            return false;
        }

        try {
            reminderLogRepository.save(NotificationReminderLog.builder()
                    .userId(user.getId())
                    .reminderType(kind.type.name())
                    .localDate(localDate)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            return false;
        }

        Map<String, String> vars = kind.type == NotificationType.STREAK_REMINDER
                ? templateService.streakVars(user.getCurrentStreak())
                : Map.of();

        // Render once with the right rotation index so each user sees variety
        // across days. NotificationService renders the user's locale via
        // notifyTemplated, so we wrap rendering here only for the rotation
        // selection then pass title/body via the notify(...) signature.
        int variations = templateService.variationCount(kind.type, user.getPreferredLanguage());
        int index = templateService.rotationIndex(user.getId(), localDate, variations);
        var rendered = templateService.render(kind.type, user.getPreferredLanguage(), vars, index);

        notificationService.notify(
                user.getId(),
                kind.type,
                rendered.title(),
                rendered.body(),
                kind.targetType,
                null,
                kind.eventIdPrefix + ":" + user.getId() + ":" + localDate,
                null
        );
        return true;
    }

    private ReminderKind decideKind(User user, LocalDate localDate) {
        LocalDate accountStart = user.getCreatedAt() == null
                ? localDate
                : user.getCreatedAt().toLocalDate();
        long accountAgeDays = localDate.toEpochDay() - accountStart.toEpochDay();

        if (user.getTotalEntries() == 0) {
            if (accountAgeDays <= FIRST_JOURNAL_GRACE_DAYS) {
                return new ReminderKind(NotificationType.FIRST_JOURNAL_REMINDER, "JOURNAL", "first_journal");
            }
            return null;
        }

        LocalDate lastEntry = user.getLastEntryDate();
        boolean wroteToday = lastEntry != null && lastEntry.isEqual(localDate);
        long daysSinceLast = lastEntry == null
                ? Long.MAX_VALUE
                : localDate.toEpochDay() - lastEntry.toEpochDay();

        if (!wroteToday && user.getCurrentStreak() >= STREAK_REMINDER_MIN && daysSinceLast == 1) {
            return new ReminderKind(NotificationType.STREAK_REMINDER, "JOURNAL", "streak_reminder");
        }

        if (daysSinceLast >= INACTIVITY_DAYS_THRESHOLD && accountAgeDays > FIRST_JOURNAL_GRACE_DAYS) {
            return new ReminderKind(NotificationType.INACTIVITY_REMINDER, "JOURNAL", "inactivity");
        }

        if (wroteToday) {
            // Active user already did the work today; no nag.
            return null;
        }

        return new ReminderKind(NotificationType.DAILY_REMINDER, "JOURNAL", "daily_reminder");
    }

    private ZoneId resolveZone(NotificationPreference pref, User user) {
        String tz = pref.getTimezone();
        if (tz == null || tz.isBlank()) tz = user.getTimezone();
        if (tz == null || tz.isBlank()) tz = "UTC";
        try {
            return ZoneId.of(tz);
        } catch (Exception ex) {
            return ZoneId.of("UTC");
        }
    }

    private record ReminderKind(NotificationType type, String targetType, String eventIdPrefix) {}
}
