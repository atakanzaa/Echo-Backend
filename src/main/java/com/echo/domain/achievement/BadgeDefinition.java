package com.echo.domain.achievement;

import com.echo.domain.user.User;

public enum BadgeDefinition {
    FIRST_ENTRY("First Steps", "👣") {
        @Override public boolean isEarned(User user) { return user.getTotalEntries() >= 1; }
    },
    SEVEN_DAY_STREAK("Consistency King", "👑") {
        @Override public boolean isEarned(User user) { return user.getCurrentStreak() >= 7; }
    },
    THIRTY_DAY_STREAK("Monthly Master", "🏆") {
        @Override public boolean isEarned(User user) { return user.getCurrentStreak() >= 30; }
    },
    MOOD_EXPLORER("Mood Explorer", "🎯") {
        @Override public boolean isEarned(User user) { return user.getTotalEntries() >= 10; }
    },
    SELF_REFLECTION_MASTER("Self-Reflection Master", "🌟") {
        @Override public boolean isEarned(User user) { return user.getTotalEntries() >= 50; }
    },
    ZEN_MASTER("Zen Master", "☮️") {
        @Override public boolean isEarned(User user) { return user.getLongestStreak() >= 60; }
    };

    private final String title;
    private final String emoji;

    BadgeDefinition(String title, String emoji) {
        this.title = title;
        this.emoji = emoji;
    }

    public String getTitle() { return title; }
    public String getEmoji() { return emoji; }
    public abstract boolean isEarned(User user);
}
