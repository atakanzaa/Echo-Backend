package com.echo.service;

import com.echo.ai.AISynthesisResponse;
import com.echo.domain.user.UserProfileSummary;
import com.echo.repository.UserProfileSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

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
