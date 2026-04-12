package com.echo.dto.response;

import com.echo.domain.user.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID   id,
        String email,
        String displayName,
        String timezone,
        int    currentStreak,
        int    longestStreak,
        int    totalEntries,
        BigDecimal moodScoreAvg,
        String language,
        boolean emailVerified,
        boolean passwordLoginEnabled
) {
    public static UserResponse from(User user) {
        // Recompute effective streak at read time: if the user missed yesterday, streak is broken
        LocalDate lastEntry = user.getLastEntryDate();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int effectiveStreak = (lastEntry == null || lastEntry.isBefore(yesterday))
                ? 0
                : user.getCurrentStreak();

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTimezone(),
                effectiveStreak,
                user.getLongestStreak(),
                user.getTotalEntries(),
                user.getMoodScoreAvg(),
                user.getPreferredLanguage(),
                user.isEmailVerified(),
                user.isPasswordLoginEnabled()
        );
    }
}
