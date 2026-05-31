package com.echo.domain.achievement;

import com.echo.domain.user.User;

public enum BadgeDefinition {
    FIRST_ENTRY("First Steps", "👣",
            "You took the first step on your journaling journey.",
            "Record your first journal entry") {
        @Override public boolean isEarned(User user) { return user.getTotalEntries() >= 1; }
        @Override public double getProgress(User user) { return Math.min(1.0, user.getTotalEntries() / 1.0); }
        @Override public int getRemaining(User user) { return Math.max(0, 1 - user.getTotalEntries()); }
    },
    SEVEN_DAY_STREAK("Consistency King", "👑",
            "You built a seven-day reflection streak.",
            "Journal 7 days in a row") {
        @Override public boolean isEarned(User user) { return user.getCurrentStreak() >= 7; }
        @Override public double getProgress(User user) { return Math.min(1.0, user.getCurrentStreak() / 7.0); }
        @Override public int getRemaining(User user) { return Math.max(0, 7 - user.getCurrentStreak()); }
    },
    THIRTY_DAY_STREAK("Monthly Master", "🏆",
            "You sustained journaling for an entire month.",
            "Journal 30 days in a row") {
        @Override public boolean isEarned(User user) { return user.getCurrentStreak() >= 30; }
        @Override public double getProgress(User user) { return Math.min(1.0, user.getCurrentStreak() / 30.0); }
        @Override public int getRemaining(User user) { return Math.max(0, 30 - user.getCurrentStreak()); }
    },
    MOOD_EXPLORER("Mood Explorer", "🎯",
            "You explored your emotional patterns across multiple entries.",
            "Record 10 journal entries") {
        @Override public boolean isEarned(User user) { return user.getTotalEntries() >= 10; }
        @Override public double getProgress(User user) { return Math.min(1.0, user.getTotalEntries() / 10.0); }
        @Override public int getRemaining(User user) { return Math.max(0, 10 - user.getTotalEntries()); }
    },
    SELF_REFLECTION_MASTER("Self-Reflection Master", "🌟",
            "You demonstrated deep and sustained self-reflection.",
            "Record 50 journal entries") {
        @Override public boolean isEarned(User user) { return user.getTotalEntries() >= 50; }
        @Override public double getProgress(User user) { return Math.min(1.0, user.getTotalEntries() / 50.0); }
        @Override public int getRemaining(User user) { return Math.max(0, 50 - user.getTotalEntries()); }
    },
    ZEN_MASTER("Zen Master", "☮️",
            "You achieved exceptional long-term consistency.",
            "Reach a 60-day longest streak") {
        @Override public boolean isEarned(User user) { return user.getLongestStreak() >= 60; }
        @Override public double getProgress(User user) { return Math.min(1.0, user.getLongestStreak() / 60.0); }
        @Override public int getRemaining(User user) { return Math.max(0, 60 - user.getLongestStreak()); }
    };

    private final String title;
    private final String emoji;
    private final String description;
    private final String criteriaDescription;

    BadgeDefinition(String title, String emoji, String description, String criteriaDescription) {
        this.title = title;
        this.emoji = emoji;
        this.description = description;
        this.criteriaDescription = criteriaDescription;
    }

    public String getTitle() { return title; }
    public String getEmoji() { return emoji; }
    public String getDescription() { return description; }
    public String getCriteriaDescription() { return criteriaDescription; }
    public abstract boolean isEarned(User user);
    public abstract double getProgress(User user);
    public abstract int getRemaining(User user);
}
