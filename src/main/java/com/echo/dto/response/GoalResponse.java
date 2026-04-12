package com.echo.dto.response;

import com.echo.domain.goal.Goal;
import com.echo.domain.goal.GoalCompletionType;
import com.echo.domain.goal.GoalCreationType;
import com.echo.domain.goal.GoalStatus;

import java.util.UUID;

public record GoalResponse(
        UUID   id,
        String title,
        String timeframe,
        String goalType,
        GoalCreationType creationType,
        GoalStatus status,
        UUID   sourceJournalEntryId,
        String detectedAt,
        String completedAt,
        GoalCompletionType completionType
) {
    public static GoalResponse from(Goal goal) {
        return new GoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getTimeframe(),
                goal.getGoalType(),
                goal.getCreationType(),
                goal.getStatus(),
                goal.getSourceJournalEntryId(),
                goal.getDetectedAt() != null ? goal.getDetectedAt().toString() : null,
                goal.getCompletedAt() != null ? goal.getCompletedAt().toString() : null,
                goal.getCompletionType()
        );
    }
}
