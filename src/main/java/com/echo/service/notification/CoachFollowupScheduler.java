package com.echo.service.notification;

import com.echo.domain.coach.CoachSession;
import com.echo.domain.notification.NotificationType;
import com.echo.repository.CoachSessionRepository;
import com.echo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sends a single follow-up nudge ~48h after a Coach session is closed.
 * Polled hourly; idempotency is handled by `notifications.event_id` UNIQUE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachFollowupScheduler {

    private final CoachSessionRepository coachSessionRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 30 * * * *", zone = "UTC")
    @Transactional
    public void runHourly() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowStart = now.minusHours(49);
        OffsetDateTime windowEnd = now.minusHours(48);
        List<CoachSession> ended = coachSessionRepository.findEndedBetween(windowStart, windowEnd);
        if (ended.isEmpty()) return;

        int sent = 0;
        for (CoachSession session : ended) {
            try {
                notificationService.notifyTemplated(
                        session.getUser().getId(),
                        NotificationType.COACH_FOLLOWUP,
                        Map.of(),
                        "COACH",
                        session.getId(),
                        "coach_followup:" + session.getId(),
                        null
                );
                sent++;
            } catch (Exception ex) {
                log.warn("Coach follow-up notify failed sessionId={}: {}",
                        session.getId(), ex.getMessage());
            }
        }
        log.info("Coach follow-up: {} sessions matched, {} notified", ended.size(), sent);
    }
}
