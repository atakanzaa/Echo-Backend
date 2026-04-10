package com.echo.service;

import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.notification.NotificationType;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Detects prolonged low mood (5+ consecutive days with moodScore < 0.3)
 * and sends a gentle notification suggesting the user talk to the Coach.
 * Runs daily at 10:00 AM. No AI calls — pure rule-based.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoodAlertService {

    private static final int LOW_MOOD_STREAK_THRESHOLD = 5;
    private static final BigDecimal LOW_MOOD_SCORE = new BigDecimal("0.30");

    private final AnalysisResultRepository analysisResultRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 10 * * *")
    public void checkMoodPatterns() {
        LocalDate since = LocalDate.now().minusDays(7);
        var activeUsers = userRepo.findUsersWithRecentEntries(since);
        log.info("Mood pattern check started: {} active users", activeUsers.size());

        int alerts = 0;
        for (var user : activeUsers) {
            if (hasLowMoodStreak(user.getId())) {
                sendLowMoodAlert(user.getId(), user.getPreferredLanguage());
                alerts++;
            }
        }

        log.info("Mood pattern check completed: {} alerts sent", alerts);
    }

    private boolean hasLowMoodStreak(java.util.UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(LOW_MOOD_STREAK_THRESHOLD);

        List<AnalysisResult> recent = analysisResultRepo
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(userId, windowStart, today);

        if (recent.size() < LOW_MOOD_STREAK_THRESHOLD) {
            return false;
        }

        // Check if the last N entries are all below threshold
        return recent.stream()
                .limit(LOW_MOOD_STREAK_THRESHOLD)
                .allMatch(r -> r.getMoodScore() != null && r.getMoodScore().compareTo(LOW_MOOD_SCORE) < 0);
    }

    private void sendLowMoodAlert(java.util.UUID userId, String language) {
        boolean turkish = "tr".equalsIgnoreCase(language);
        String title = turkish
                ? "Seni düşünüyoruz"
                : "We're thinking of you";
        String body = turkish
                ? "Son günlerde biraz zor bir dönemden geçiyor olabilirsin. Coach ile konuşmak ister misin?"
                : "You may be going through a tough time lately. Would you like to talk to your Coach?";

        String eventId = "mood_alert:" + userId + ":" + LocalDate.now();

        notificationService.notify(
                userId,
                NotificationType.MOOD_ALERT,
                title,
                body,
                "COACH",
                null,
                eventId,
                null
        );
    }
}
