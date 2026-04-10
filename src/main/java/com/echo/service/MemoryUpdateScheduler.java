package com.echo.service;

import com.echo.domain.notification.NotificationType;
import com.echo.domain.user.UserProfileSummary;
import com.echo.repository.UserProfileSummaryRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Weekly user profile update scheduler.
 * Every Sunday at 02:00: runs synthesis for active users → updates UserProfileSummary.
 * Every Sunday at 09:00: sends weekly reflection notification with the digest.
 * Cost: 300 active users × 1 call/week ≈ $0.36/month
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryUpdateScheduler {

    private final UserRepository            userRepo;
    private final AISynthesisService        synthesisService;
    private final NotificationService       notificationService;
    private final UserProfileSummaryRepository profileSummaryRepo;

    @Scheduled(cron = "0 0 2 * * SUN")
    public void updateWeeklyProfiles() {
        LocalDate since = LocalDate.now().minusDays(7);
        var activeUsers = userRepo.findUsersWithRecentEntries(since);
        log.info("Weekly profile update started: {} active users", activeUsers.size());

        int success = 0;
        int failed  = 0;
        for (var user : activeUsers) {
            try {
                synthesisService.synthesize(user.getId(), 7);
                success++;
            } catch (Exception e) {
                log.warn("Profile update failed userId={}: {}", user.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Weekly profile update completed: {} success, {} failed", success, failed);
    }

    @Scheduled(cron = "0 0 9 * * SUN")
    public void sendWeeklyReflections() {
        LocalDate since = LocalDate.now().minusDays(7);
        var activeUsers = userRepo.findUsersWithRecentEntries(since);
        log.info("Weekly reflection notifications: {} active users", activeUsers.size());

        int sent = 0;
        for (var user : activeUsers) {
            try {
                profileSummaryRepo.findByUserId(user.getId()).ifPresent(profile -> {
                    String digest = profile.getWeeklyDigest();
                    if (digest != null && !digest.isBlank()) {
                        boolean turkish = "tr".equalsIgnoreCase(user.getPreferredLanguage());
                        String title = turkish
                                ? "Haftalık Yansıman Hazır"
                                : "Your Weekly Reflection is Ready";

                        // Truncate for notification body (max 200 chars)
                        String body = digest.length() > 200
                                ? digest.substring(0, 197) + "..."
                                : digest;

                        String eventId = "weekly_reflection:" + user.getId() + ":" + LocalDate.now();

                        notificationService.notify(
                                user.getId(),
                                NotificationType.WEEKLY_REFLECTION,
                                title,
                                body,
                                "INSIGHTS",
                                null,
                                eventId,
                                null
                        );
                    }
                });
                sent++;
            } catch (Exception e) {
                log.warn("Weekly reflection notification failed userId={}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("Weekly reflection notifications sent: {}", sent);
    }
}
