package com.echo.service.notification;

import com.echo.domain.notification.Notification;
import com.echo.domain.notification.NotificationPreference;
import com.echo.domain.notification.NotificationType;
import com.echo.domain.notification.PushToken;
import com.echo.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Decides whether a given user is eligible to receive a push for a given
 * notification. Reads NotificationPreference for master + per-category +
 * quiet-hours gating. Returns the active push tokens when eligible, or an
 * empty list with a reason when not.
 */
@Service
@RequiredArgsConstructor
public class NotificationEligibilityService {

    private final PushTokenRepository pushTokenRepository;
    private final NotificationPreferenceService preferenceService;

    public Decision evaluate(Notification notification) {
        UUID userId = notification.getUser().getId();
        NotificationPreference pref = preferenceService.getOrCreate(userId);

        if (!pref.isCategoryEnabled(notification.getType().category())) {
            return Decision.skipDisabled();
        }

        if (isInQuietHours(pref, notification.getType())) {
            return Decision.skipQuietHours();
        }

        List<PushToken> tokens = pushTokenRepository.findByUserIdAndActiveTrue(userId);
        if (tokens.isEmpty()) {
            return Decision.skipNoToken();
        }

        return Decision.deliver(tokens);
    }

    private boolean isInQuietHours(NotificationPreference pref, NotificationType type) {
        if (pref.getQuietHoursStartLocal() == null || pref.getQuietHoursEndLocal() == null) {
            return false;
        }
        // Time-sensitive types bypass quiet hours so urgent updates still land.
        if (type == NotificationType.MOOD_ALERT) return false;

        String tz = pref.getTimezone();
        if (tz == null || tz.isBlank()) {
            tz = pref.getUser().getTimezone();
            if (tz == null || tz.isBlank()) tz = "UTC";
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(tz);
        } catch (Exception ex) {
            zone = ZoneId.of("UTC");
        }

        int hour = ZonedDateTime.now(zone).getHour();
        int start = pref.getQuietHoursStartLocal();
        int end = pref.getQuietHoursEndLocal();
        if (start == end) return false;
        if (start < end) {
            return hour >= start && hour < end;
        }
        // Wrap-around window (e.g. 22 -> 7).
        return hour >= start || hour < end;
    }

    public record Decision(Outcome outcome, List<PushToken> tokens) {
        public enum Outcome { DELIVER, SKIP_DISABLED, SKIP_QUIET_HOURS, SKIP_NO_TOKEN }

        public static Decision deliver(List<PushToken> tokens) {
            return new Decision(Outcome.DELIVER, tokens);
        }
        public static Decision skipDisabled() { return new Decision(Outcome.SKIP_DISABLED, List.of()); }
        public static Decision skipQuietHours() { return new Decision(Outcome.SKIP_QUIET_HOURS, List.of()); }
        public static Decision skipNoToken() { return new Decision(Outcome.SKIP_NO_TOKEN, List.of()); }
    }
}
