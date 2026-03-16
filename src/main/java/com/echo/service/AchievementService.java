package com.echo.service;

import com.echo.domain.achievement.BadgeDefinition;
import com.echo.domain.achievement.UserAchievement;
import com.echo.domain.user.User;
import com.echo.dto.response.AchievementsResponse;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.UserAchievementRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    private final UserRepository            userRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AnalysisResultRepository  analysisResultRepository;
    private final AISynthesisService        synthesisService;

    @Transactional
    public void checkAndAward(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();

        // Streak hesapla
        updateStreak(user);

        // Badge kontrolü
        Set<String> earned = userAchievementRepository.findByUserId(userId)
                .stream().map(UserAchievement::getBadgeKey).collect(Collectors.toSet());

        for (BadgeDefinition badge : BadgeDefinition.values()) {
            if (!earned.contains(badge.name()) && badge.isEarned(user)) {
                var achievement = UserAchievement.builder()
                        .user(user)
                        .badgeKey(badge.name())
                        .build();
                userAchievementRepository.save(achievement);
                log.info("Badge kazanıldı: userId={}, badge={}", userId, badge.name());
            }
        }

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AchievementsResponse getAchievements(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<UserAchievement> earned = userAchievementRepository.findByUserId(userId);
        Set<String> earnedKeys = earned.stream().map(UserAchievement::getBadgeKey).collect(Collectors.toSet());

        int totalWords = estimateTotalWords(userId);

        List<AchievementsResponse.BadgeItem> badges = Arrays.stream(BadgeDefinition.values())
                .map(b -> new AchievementsResponse.BadgeItem(
                        b.name(),
                        b.name(),
                        b.getTitle(),
                        b.getEmoji(),
                        earnedKeys.contains(b.name()),
                        earned.stream()
                                .filter(a -> a.getBadgeKey().equals(b.name()))
                                .findFirst()
                                .map(a -> a.getEarnedAt().toString())
                                .orElse(null)
                ))
                .toList();

        GrowthAssessment growth = generateGrowthAssessment(userId, user);

        return new AchievementsResponse(
                user.getCurrentStreak(),
                user.getLongestStreak(),
                user.getTotalEntries(),
                totalWords,
                growth.score(),
                badges,
                growth.label(),
                growth.message()
        );
    }

    private void updateStreak(User user) {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate lastEntry = user.getLastEntryDate();

        if (lastEntry == null || lastEntry.isBefore(yesterday)) {
            // Streak sıfırla (dün giriş yok)
            if (lastEntry != null && !lastEntry.equals(yesterday)) {
                user.setCurrentStreak(1);
            } else {
                user.setCurrentStreak(1);
            }
        } else if (lastEntry.equals(yesterday)) {
            user.setCurrentStreak(user.getCurrentStreak() + 1);
        }
        // Bugün zaten giriş yapılmışsa streak değişmez

        user.setLongestStreak(Math.max(user.getLongestStreak(), user.getCurrentStreak()));
        user.setLastEntryDate(today);
        user.setTotalEntries(user.getTotalEntries() + 1);
    }

    private int estimateTotalWords(UUID userId) {
        return analysisResultRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        userId,
                        LocalDate.now().minusDays(365),
                        LocalDate.now())
                .stream()
                .mapToInt(r -> r.getSummary() != null
                        ? r.getSummary().split("\\s+").length * 10 : 0)
                .sum();
    }

    /**
     * AI'dan growth assessment iste — fallback olarak kural tabanlı hesaplama kullan.
     * Badge logic dokunulmadı (deterministik kalmaya devam ediyor).
     */
    private GrowthAssessment generateGrowthAssessment(UUID userId, User user) {
        try {
            var synthesis = synthesisService.synthesize(userId, 30);
            if (synthesis.growthLabel() != null && synthesis.growthMessage() != null) {
                return new GrowthAssessment(synthesis.growthScore(), synthesis.growthLabel(), synthesis.growthMessage());
            }
        } catch (Exception e) {
            log.warn("AI growth assessment oluşturulamadı, fallback kullanılıyor: {}", e.getMessage());
        }
        double score = Math.min(100.0, (user.getCurrentStreak() * 2.0) + (user.getTotalEntries() * 1.5));
        return new GrowthAssessment((int) score, computeFallbackLevel(user), computeFallbackMessage(user));
    }

    private String computeFallbackLevel(User user) {
        if (user.getCurrentStreak() >= 30) return "Top 1%";
        if (user.getCurrentStreak() >= 14) return "Top 5%";
        if (user.getCurrentStreak() >= 7)  return "Top 10%";
        return "Getting Started";
    }

    private String computeFallbackMessage(User user) {
        if (user.getCurrentStreak() >= 7) return "Harika gidiyorsun! 🔥";
        if (user.getCurrentStreak() >= 3) return "Güzel bir ivme yakaladın, devam et!";
        return "Bugün başla, küçük adımlar büyük fark yaratır.";
    }

    private record GrowthAssessment(int score, String label, String message) {}
}
