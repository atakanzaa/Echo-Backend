package com.echo.service;

import com.echo.domain.goal.Goal;
import com.echo.domain.goal.GoalSuggestion;
import com.echo.dto.response.GoalResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.GoalRepository;
import com.echo.repository.GoalSuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalSuggestionRepository goalSuggestionRepository;

    private static final List<String> OPEN_STATUSES = List.of(
            GoalIntegrationService.GOAL_STATUS_PENDING,
            GoalIntegrationService.GOAL_STATUS_ACTIVE
    );

    /** Active goals (PENDING + ACTIVE) */
    @Transactional(readOnly = true)
    public PagedResponse<GoalResponse> getActiveGoals(UUID userId, Pageable pageable) {
        return PagedResponse.from(
                goalRepository.findByUserIdAndStatusInOrderByDetectedAtDesc(userId, OPEN_STATUSES, pageable),
                GoalResponse::from
        );
    }

    /** All goals including history */
    @Transactional(readOnly = true)
    public PagedResponse<GoalResponse> getAllGoals(UUID userId, Pageable pageable) {
        return PagedResponse.from(
                goalRepository.findByUserIdAndStatusNotOrderByDetectedAtDesc(
                        userId, GoalIntegrationService.GOAL_STATUS_DELETED, pageable),
                GoalResponse::from
        );
    }

    /** Hedefi tamamlandı olarak işaretle */
    @Transactional
    public GoalResponse completeGoal(UUID userId, UUID goalId) {
        return completeGoalInternal(userId, goalId, "MANUAL", null);
    }

    @Transactional
    public GoalResponse completeGoalFromAutomation(UUID userId, UUID goalId, String sourceType, UUID sourceRefId) {
        return completeGoalInternal(userId, goalId, sourceType, sourceRefId);
    }

    /** Hedefi tamamlanmadı olarak işaretle */
    @Transactional
    public GoalResponse markNotCompleted(UUID userId, UUID goalId) {
        Goal goal = getGoal(userId, goalId);

        goal.setStatus(GoalIntegrationService.GOAL_STATUS_DISMISSED);
        goal.setCompletedAt(null);
        goal.setCompletedSourceType(null);
        goal.setCompletedSourceRefId(null);
        goal.setDeletedAt(null);
        goalRepository.save(goal);
        expirePendingSuggestions(goal.getId());

        log.info("Goal marked not completed: goalId={}, userId={}", goalId, userId);
        return GoalResponse.from(goal);
    }

    @Transactional
    public GoalResponse dismissGoal(UUID userId, UUID goalId) {
        return markNotCompleted(userId, goalId);
    }

    @Transactional
    public void deleteGoal(UUID userId, UUID goalId) {
        Goal goal = getGoal(userId, goalId);
        goal.setStatus(GoalIntegrationService.GOAL_STATUS_DELETED);
        goal.setDeletedAt(OffsetDateTime.now());
        goal.setCompletedSourceType(null);
        goal.setCompletedSourceRefId(null);
        goalRepository.save(goal);
        expirePendingSuggestions(goal.getId());

        log.info("Goal soft-deleted: goalId={}, userId={}", goalId, userId);
    }

    private GoalResponse completeGoalInternal(UUID userId, UUID goalId, String sourceType, UUID sourceRefId) {
        Goal goal = getGoal(userId, goalId);

        goal.setStatus(GoalIntegrationService.GOAL_STATUS_COMPLETED);
        goal.setCompletedAt(OffsetDateTime.now());
        goal.setCompletedSourceType(sourceType);
        goal.setCompletedSourceRefId(sourceRefId);
        goal.setDeletedAt(null);
        goalRepository.save(goal);
        expirePendingSuggestions(goal.getId());

        log.info("Goal completed: goalId={}, userId={}, sourceType={}", goalId, userId, sourceType);
        return GoalResponse.from(goal);
    }

    private Goal getGoal(UUID userId, UUID goalId) {
        return goalRepository.findByIdAndUserIdAndStatusNot(goalId, userId, GoalIntegrationService.GOAL_STATUS_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
    }

    private void expirePendingSuggestions(UUID goalId) {
        List<GoalSuggestion> suggestions = goalSuggestionRepository.findByGoalIdAndStatus(
                goalId,
                GoalIntegrationService.SUGGESTION_STATUS_PENDING
        );
        if (suggestions.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (GoalSuggestion suggestion : suggestions) {
            suggestion.setStatus(GoalIntegrationService.SUGGESTION_STATUS_EXPIRED);
            suggestion.setResolvedAt(now);
        }
        goalSuggestionRepository.saveAll(suggestions);
    }
}
