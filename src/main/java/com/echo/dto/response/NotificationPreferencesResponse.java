package com.echo.dto.response;

import com.echo.domain.notification.NotificationPreference;

public record NotificationPreferencesResponse(
        boolean masterEnabled,
        boolean dailyRemindersEnabled,
        boolean communityEnabled,
        boolean achievementsEnabled,
        boolean insightsEnabled,
        boolean moodAlertsEnabled,
        boolean capsulesEnabled,
        boolean coachEnabled,
        boolean systemEnabled,
        int preferredLocalHour,
        Integer quietHoursStartLocal,
        Integer quietHoursEndLocal,
        String timezone
) {
    public static NotificationPreferencesResponse from(NotificationPreference pref) {
        return new NotificationPreferencesResponse(
                pref.isMasterEnabled(),
                pref.isDailyRemindersEnabled(),
                pref.isCommunityEnabled(),
                pref.isAchievementsEnabled(),
                pref.isInsightsEnabled(),
                pref.isMoodAlertsEnabled(),
                pref.isCapsulesEnabled(),
                pref.isCoachEnabled(),
                pref.isSystemEnabled(),
                pref.getPreferredLocalHour(),
                pref.getQuietHoursStartLocal(),
                pref.getQuietHoursEndLocal(),
                pref.getTimezone()
        );
    }
}
