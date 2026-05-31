package com.echo.domain.notification;

public enum NotificationType {
    ACHIEVEMENT_EARNED,
    ACHIEVEMENT_PROGRESS,
    ANALYSIS_COMPLETE,
    POST_COMMENTED,
    POST_LIKED,
    COMMENT_REPLIED,
    CAPSULE_UNLOCKED,
    INSIGHT_UNLOCKED,
    MOOD_ALERT,
    WEEKLY_REFLECTION,
    DAILY_REMINDER,
    FIRST_JOURNAL_REMINDER,
    INACTIVITY_REMINDER,
    STREAK_REMINDER,
    COACH_FOLLOWUP,
    SYSTEM_ANNOUNCEMENT;

    public NotificationCategory category() {
        return switch (this) {
            case POST_COMMENTED, POST_LIKED, COMMENT_REPLIED -> NotificationCategory.COMMUNITY;
            case ACHIEVEMENT_EARNED, ACHIEVEMENT_PROGRESS -> NotificationCategory.ACHIEVEMENTS;
            case ANALYSIS_COMPLETE, INSIGHT_UNLOCKED, WEEKLY_REFLECTION -> NotificationCategory.INSIGHTS;
            case MOOD_ALERT -> NotificationCategory.MOOD_ALERTS;
            case CAPSULE_UNLOCKED -> NotificationCategory.CAPSULES;
            case COACH_FOLLOWUP -> NotificationCategory.COACH;
            case DAILY_REMINDER, FIRST_JOURNAL_REMINDER, INACTIVITY_REMINDER, STREAK_REMINDER
                    -> NotificationCategory.DAILY_REMINDERS;
            case SYSTEM_ANNOUNCEMENT -> NotificationCategory.SYSTEM;
        };
    }
}
