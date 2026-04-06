package com.echo.service;

import com.echo.ai.AISynthesisResponse;
import com.echo.domain.user.UserProfileSummary;
import com.echo.repository.UserProfileSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryService {

    private final UserProfileSummaryRepository profileRepo;

    @Transactional(readOnly = true)
    public String getUserProfile(UUID userId) {
        return profileRepo.findByUserId(userId)
                .map(this::formatProfile)
                .orElse(null);
    }

    /**
     * Her synthesis sonrası çağrılır — kullanıcı profilini AI çıktısından günceller.
     * Mevcut profil üzerine yazar (silmez, geliştirir).
     */
    @Transactional
    public void updateFromSynthesis(UUID userId, AISynthesisResponse synthesis) {
        UserProfileSummary profile = getOrCreate(userId);
        if (profile.getLastSynthesisAt() != null
                && profile.getLastSynthesisAt().isAfter(OffsetDateTime.now().minusSeconds(5))) {
            log.info("Skipping synthesis update — a more recent synthesis already applied: userId={}", userId);
            return;
        }

        if (synthesis.profileUpdate() != null) {
            AISynthesisResponse.ProfileUpdate pu = synthesis.profileUpdate();
            if (pu.userProfile()       != null) profile.setUserProfile(pu.userProfile());
            if (pu.emotionalPatterns() != null) profile.setEmotionalPatterns(pu.emotionalPatterns());
            if (pu.valuesStrengths()   != null) profile.setValuesStrengths(pu.valuesStrengths());
            if (pu.growthTrajectory()  != null) profile.setGrowthTrajectory(pu.growthTrajectory());
        }
        if (synthesis.narrativeSummary() != null) {
            profile.setWeeklyDigest(synthesis.narrativeSummary());
        }
        profile.setLastSynthesisAt(OffsetDateTime.now());
        profileRepo.save(profile);
    }

    @Transactional
    public void updateWeeklyDigest(UUID userId, String digest) {
        UserProfileSummary profile = getOrCreate(userId);
        profile.setWeeklyDigest(digest);
        profile.setLastSynthesisAt(OffsetDateTime.now());
        profileRepo.save(profile);
    }

    @Transactional
    public void updateFullProfile(UUID userId, String userProfile, String emotionalPatterns,
                                  String valuesStrengths, String growthTrajectory, String weeklyDigest) {
        UserProfileSummary profile = getOrCreate(userId);
        if (userProfile != null) profile.setUserProfile(userProfile);
        if (emotionalPatterns != null) profile.setEmotionalPatterns(emotionalPatterns);
        if (valuesStrengths != null) profile.setValuesStrengths(valuesStrengths);
        if (growthTrajectory != null) profile.setGrowthTrajectory(growthTrajectory);
        if (weeklyDigest != null) profile.setWeeklyDigest(weeklyDigest);
        profile.setLastSynthesisAt(OffsetDateTime.now());
        profileRepo.save(profile);
    }

    @Transactional
    public void appendAchievementToMemory(UUID userId, String badgeKey, String badgeTitle,
                                          int currentStreak, int totalEntries) {
        UserProfileSummary profile = getOrCreate(userId);
        String achievement = String.format(
                "Earned '%s' badge on %s. (Streak: %d, Total entries: %d)",
                badgeTitle, LocalDate.now(), currentStreak, totalEntries
        );

        String existing = profile.getGrowthTrajectory();
        if (existing == null || existing.isBlank()) {
            profile.setGrowthTrajectory(achievement);
        } else {
            String[] lines = existing.split("\n");
            if (lines.length >= 10) {
                existing = String.join("\n",
                        Arrays.copyOfRange(lines, lines.length - 9, lines.length));
            }
            profile.setGrowthTrajectory(existing + "\n" + achievement);
        }
        profileRepo.save(profile);
    }

    private UserProfileSummary getOrCreate(UUID userId) {
        return profileRepo.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfileSummary p = new UserProfileSummary();
                    p.setUserId(userId);
                    return p;
                });
    }

    private String formatProfile(UserProfileSummary p) {
        StringBuilder sb = new StringBuilder();
        if (p.getUserProfile() != null) sb.append("PROFİL: ").append(p.getUserProfile()).append("\n");
        if (p.getEmotionalPatterns() != null) sb.append("DUYGUSAL KALIPLAR: ").append(p.getEmotionalPatterns()).append("\n");
        if (p.getValuesStrengths() != null) sb.append("GÜÇLÜ YÖNLER: ").append(p.getValuesStrengths()).append("\n");
        if (p.getGrowthTrajectory() != null) sb.append("GELİŞİM: ").append(p.getGrowthTrajectory()).append("\n");
        if (p.getWeeklyDigest() != null) sb.append("SON HAFTA: ").append(p.getWeeklyDigest());
        return sb.length() > 0 ? sb.toString() : null;
    }
}
