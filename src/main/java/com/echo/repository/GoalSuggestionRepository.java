package com.echo.repository;

import com.echo.domain.goal.GoalSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalSuggestionRepository extends JpaRepository<GoalSuggestion, UUID> {

    Optional<GoalSuggestion> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByDedupeKey(String dedupeKey);

    List<GoalSuggestion> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    List<GoalSuggestion> findByUserIdAndStatusAndSourceTypeAndSourceJournalEntryIdOrderByCreatedAtDesc(
            UUID userId, String status, String sourceType, UUID sourceJournalEntryId);

    List<GoalSuggestion> findByUserIdAndStatusAndSourceTypeAndSourceCoachSessionIdOrderByCreatedAtDesc(
            UUID userId, String status, String sourceType, UUID sourceCoachSessionId);

    List<GoalSuggestion> findByUserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID userId, String status, OffsetDateTime createdAt);

    List<GoalSuggestion> findByUserIdAndStatusAndGoalIdAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID userId, String status, UUID goalId, OffsetDateTime createdAt);

    List<GoalSuggestion> findByGoalIdAndStatus(UUID goalId, String status);

    List<GoalSuggestion> findByUserIdAndStatusAndExpiresAtBefore(UUID userId, String status, OffsetDateTime before);
}
