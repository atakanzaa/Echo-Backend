package com.echo.service;

import com.echo.domain.achievement.BadgeDefinition;
import com.echo.domain.achievement.UserAchievement;
import com.echo.domain.user.User;
import com.echo.dto.response.AchievementDetailResponse;
import com.echo.dto.response.AchievementsResponse;
import com.echo.event.AchievementEarnedEvent;
import jakarta.persistence.EntityManager;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserAchievementRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    private final UserRepository            userRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final JournalEntryRepository    journalEntryRepository;
    private final AISynthesisService        synthesisService;
    private final UserMemoryService         userMemoryService;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager              entityManager;
    private final ApplicationEventPublisher  eventPublisher;

    public void checkAndAward(UUID userId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> doCheckAndAward(userId));
                return;
            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict on checkAndAward, attempt {}/3: userId={}", attempt + 1, userId);
                entityManager.clear();
                if (attempt == 2) {
                    log.error("checkAndAward failed after 3 attempts: userId={}", userId);
                }
            }
        }
    }

    private void doCheckAndAward(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();

        // Update streak.
        updateStreak(user);

        // Check and award badges.
        Set<String> earned = userAchievementRepository.findByUserId(userId)
                .stream().map(UserAchievement::getBadgeKey).collect(Collectors.toSet());

        for (BadgeDefinition badge : BadgeDefinition.values()) {
            if (!earned.contains(badge.name()) && badge.isEarned(user)) {
                var achievement = UserAchievement.builder()
                        .user(user)
                        .badgeKey(badge.name())
                        .build();
                userAchievementRepository.save(achievement);
                userMemoryService.appendAchievementToMemory(
                        userId,
                        badge.name(),
                        badge.getTitle(),
                        user.getCurrentStreak(),
                        user.getTotalEntries()
                );
                eventPublisher.publishEvent(
                        new AchievementEarnedEvent(userId, badge.name(), badge.getTitle(), badge.getEmoji())
                );
                log.info("Badge awarded: userId={}, badge={}", userId, badge.name());
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

    @Transactional(readOnly = true)
    public AchievementDetailResponse getAchievementDetail(UUID userId, String badgeKey) {
        User user = userRepository.findById(userId).orElseThrow();
        BadgeDefinition badge = resolveBadge(badgeKey);

        Optional<UserAchievement> earnedAchievement =
                userAchievementRepository.findByUserIdAndBadgeKey(userId, badge.name());

        return new AchievementDetailResponse(
                badge.name(),
                badge.getTitle(),
                badge.getEmoji(),
                badge.getDescription(),
                badge.getCriteriaDescription(),
                earnedAchievement.isPresent(),
                earnedAchievement.map(a -> a.getEarnedAt().toString()).orElse(null),
                badge.getProgress(user),
                generateShareText(badge.name())
        );
    }

    public String generateShareText(String badgeKey) {
        BadgeDefinition badge = resolveBadge(badgeKey);
        return switch (badge) {
            case FIRST_ENTRY ->
                    "I just started my journaling journey on Echo! Taking the first step toward self-reflection. "
                            + "👣 #Echo #FirstSteps";
            case SEVEN_DAY_STREAK ->
                    "7 days of consistent journaling on Echo! Building a habit, one entry at a time. "
                            + "👑 #Echo #ConsistencyKing";
            case THIRTY_DAY_STREAK ->
                    "30 days strong on Echo! Consistency is becoming a lifestyle. "
                            + "🏆 #Echo #MonthlyMaster";
            case MOOD_EXPLORER ->
                    "I unlocked Mood Explorer on Echo by reflecting across 10 entries. "
                            + "🎯 #Echo #MoodExplorer";
            case SELF_REFLECTION_MASTER ->
                    "Self-Reflection Master unlocked on Echo. Growth happens one honest entry at a time. "
                            + "🌟 #Echo #SelfReflection";
            case ZEN_MASTER ->
                    "Zen Master achieved on Echo! Long-term consistency pays off. "
                            + "☮️ #Echo #ZenMaster";
        };
    }

    private void updateStreak(User user) {
        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate lastEntry = user.getLastEntryDate();

        if (lastEntry == null || lastEntry.isBefore(yesterday)) {
            user.setCurrentStreak(1);
        } else if (lastEntry.equals(yesterday)) {
            user.setCurrentStreak(user.getCurrentStreak() + 1);
        }
        // Streak unchanged if user already journaled today.

        user.setLongestStreak(Math.max(user.getLongestStreak(), user.getCurrentStreak()));
        user.setLastEntryDate(today);
        user.setTotalEntries(user.getTotalEntries() + 1);
    }

    private int estimateTotalWords(UUID userId) {
        return (int) journalEntryRepository.countTotalWordsByUserId(userId);
    }

    // Request AI growth assessment — use rule-based fallback if no cached synthesis available.
    // Never triggers a blocking AI call; synthesis is pre-populated by Insights page or session-end async.
    private GrowthAssessment generateGrowthAssessment(UUID userId, User user) {
        try {
            var synthesis = synthesisService.getLatestCachedSynthesis(userId);
            if (synthesis != null && synthesis.growthLabel() != null && synthesis.growthMessage() != null) {
                return new GrowthAssessment(synthesis.growthScore(), synthesis.growthLabel(), synthesis.growthMessage());
            }
        } catch (Exception e) {
            log.warn("Cached growth lookup failed: {}", e.getMessage());
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

    private BadgeDefinition resolveBadge(String badgeKey) {
        try {
            return BadgeDefinition.valueOf(badgeKey.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid badge key: " + badgeKey);
        }
    }

    private record GrowthAssessment(int score, String label, String message) {}
}
