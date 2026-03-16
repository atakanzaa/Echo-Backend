package com.echo.dto.response;

import com.echo.domain.goal.Goal;

import java.util.UUID;

public record GoalResponse(
        UUID   id,
        String title,
        String timeframe,
        String goalType,
        String status,
        UUID   sourceJournalEntryId,
        String detectedAt,
        String completedAt
) {
    public static GoalResponse from(Goal goal) {
        return new GoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getTimeframe(),
                goal.getGoalType(),
                goal.getStatus(),
                goal.getSourceJournalEntryId(),
                goal.getDetectedAt() != null ? goal.getDetectedAt().toString() : null,
                goal.getCompletedAt() != null ? goal.getCompletedAt().toString() : null
        );
    }
}
