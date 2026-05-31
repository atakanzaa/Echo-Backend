package com.echo.service.notification;

import com.echo.domain.notification.NotificationPreference;
import com.echo.domain.user.User;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.NotificationPreferenceRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private static final int DEFAULT_PREFERRED_HOUR = 20;

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    @Transactional
    public NotificationPreference getOrCreate(UUID userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefault(userId));
    }

    @Transactional
    public NotificationPreference update(UUID userId, NotificationPreferencePatch patch) {
        NotificationPreference pref = getOrCreate(userId);
        if (patch.masterEnabled() != null) pref.setMasterEnabled(patch.masterEnabled());
        if (patch.dailyRemindersEnabled() != null) pref.setDailyRemindersEnabled(patch.dailyRemindersEnabled());
        if (patch.communityEnabled() != null) pref.setCommunityEnabled(patch.communityEnabled());
        if (patch.achievementsEnabled() != null) pref.setAchievementsEnabled(patch.achievementsEnabled());
        if (patch.insightsEnabled() != null) pref.setInsightsEnabled(patch.insightsEnabled());
        if (patch.moodAlertsEnabled() != null) pref.setMoodAlertsEnabled(patch.moodAlertsEnabled());
        if (patch.capsulesEnabled() != null) pref.setCapsulesEnabled(patch.capsulesEnabled());
        if (patch.coachEnabled() != null) pref.setCoachEnabled(patch.coachEnabled());
        if (patch.systemEnabled() != null) pref.setSystemEnabled(patch.systemEnabled());
        if (patch.preferredLocalHour() != null) {
            int hour = clampHour(patch.preferredLocalHour());
            pref.setPreferredLocalHour(hour);
        }
        if (patch.quietHoursStartLocal() != null) {
            pref.setQuietHoursStartLocal(normalizeNullableHour(patch.quietHoursStartLocal()));
        }
        if (patch.quietHoursEndLocal() != null) {
            pref.setQuietHoursEndLocal(normalizeNullableHour(patch.quietHoursEndLocal()));
        }
        if (patch.timezone() != null && !patch.timezone().isBlank()) {
            pref.setTimezone(patch.timezone().trim());
        }
        return preferenceRepository.save(pref);
    }

    private NotificationPreference createDefault(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        NotificationPreference pref = NotificationPreference.builder()
                .user(user)
                .preferredLocalHour(DEFAULT_PREFERRED_HOUR)
                .timezone(user.getTimezone())
                .build();
        try {
            return preferenceRepository.save(pref);
        } catch (DataIntegrityViolationException ex) {
            return preferenceRepository.findByUserId(userId)
                    .orElseThrow(() -> ex);
        }
    }

    private int clampHour(int hour) {
        if (hour < 0) return 0;
        if (hour > 23) return 23;
        return hour;
    }

    private Integer normalizeNullableHour(Integer hour) {
        if (hour == null || hour < 0) return null;
        if (hour > 23) return 23;
        return hour;
    }

    public record NotificationPreferencePatch(
            Boolean masterEnabled,
            Boolean dailyRemindersEnabled,
            Boolean communityEnabled,
            Boolean achievementsEnabled,
            Boolean insightsEnabled,
            Boolean moodAlertsEnabled,
            Boolean capsulesEnabled,
            Boolean coachEnabled,
            Boolean systemEnabled,
            Integer preferredLocalHour,
            Integer quietHoursStartLocal,
            Integer quietHoursEndLocal,
            String timezone
    ) {}
}
