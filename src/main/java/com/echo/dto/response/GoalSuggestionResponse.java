package com.echo.dto.response;

import com.echo.domain.goal.GoalSuggestion;

import java.util.UUID;

public record GoalSuggestionResponse(
        UUID id,
        UUID goalId,
        String suggestionType,
        String sourceType,
        String title,
        String timeframe,
        String goalType,
        String detectedText,
        String reason,
        Double confidence,
        String message,
        String primaryActionLabel,
        String secondaryActionLabel,
        String createdAt,
        String expiresAt
) {
    public static GoalSuggestionResponse from(
            GoalSuggestion suggestion,
            String message,
            String primaryActionLabel,
            String secondaryActionLabel
    ) {
        return new GoalSuggestionResponse(
                suggestion.getId(),
                suggestion.getGoal() != null ? suggestion.getGoal().getId() : null,
                suggestion.getSuggestionType(),
                suggestion.getSourceType(),
                suggestion.getTitle(),
                suggestion.getTimeframe(),
                suggestion.getGoalType(),
                suggestion.getDetectedText(),
                suggestion.getReason(),
                suggestion.getConfidence() != null ? suggestion.getConfidence().doubleValue() : null,
                message,
                primaryActionLabel,
                secondaryActionLabel,
                suggestion.getCreatedAt() != null ? suggestion.getCreatedAt().toString() : null,
                suggestion.getExpiresAt() != null ? suggestion.getExpiresAt().toString() : null
        );
    }
}
