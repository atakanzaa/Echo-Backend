package com.echo.dto.response;

import com.echo.domain.user.User;

import java.math.BigDecimal;
import java.util.UUID;

public record UserResponse(
        UUID   id,
        String email,
        String displayName,
        String timezone,
        int    currentStreak,
        int    longestStreak,
        int    totalEntries,
        BigDecimal moodScoreAvg
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTimezone(),
                user.getCurrentStreak(),
                user.getLongestStreak(),
                user.getTotalEntries(),
                user.getMoodScoreAvg()
        );
    }
}
