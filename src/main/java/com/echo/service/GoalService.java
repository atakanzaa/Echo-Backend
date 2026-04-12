package com.echo.service;

import com.echo.domain.goal.Goal;
import com.echo.domain.goal.GoalCompletionType;
import com.echo.domain.goal.GoalCreationType;
import com.echo.domain.goal.GoalStatus;
import com.echo.domain.goal.GoalSuggestion;
import com.echo.domain.subscription.FeatureKey;
import com.echo.domain.user.User;
import com.echo.dto.request.CreateGoalRequest;
import com.echo.dto.response.GoalResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.exception.QuotaExceededException;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.GoalRepository;
import com.echo.repository.GoalSuggestionRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalSuggestionRepository goalSuggestionRepository;
    private final UserRepository userRepository;
    private final EntitlementService entitlementService;

    private static final List<GoalStatus> OPEN_STATUSES = GoalStatus.openStatuses();

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
                        userId, GoalStatus.DELETED, pageable),
                GoalResponse::from
        );
    }

    @Transactional
    public GoalResponse createManualGoal(UUID userId, CreateGoalRequest request) {
        List<Goal> openGoals = goalRepository.findByUserIdAndStatusInOrderByDetectedAtDesc(userId, OPEN_STATUSES);
        Goal existing = findDuplicateGoal(openGoals, request.title());
        if (existing != null) {
            return GoalResponse.from(existing);
        }

        int limit = entitlementService.getLimit(userId, FeatureKey.ACTIVE_GOALS);
        if (limit != -1 && openGoals.size() >= limit) {
            throw new QuotaExceededException(
                    "GOALS_LIMIT",
                    "Active goal limit reached. Upgrade to Premium for more goals."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var goal = Goal.builder()
                .user(user)
                .title(compactText(request.title()))
                .timeframe(compactText(request.timeframe()))
                .goalType("general")
                .creationType(GoalCreationType.MANUAL)
                .status(GoalStatus.PENDING)
                .detectedAt(OffsetDateTime.now())
                .build();

        Goal saved = goalRepository.save(goal);
        log.info("Manual goal created: goalId={}, userId={}", saved.getId(), userId);
        return GoalResponse.from(saved);
    }

    /** Hedefi tamamlandı olarak işaretle */
    @Transactional
    public GoalResponse completeGoal(UUID userId, UUID goalId) {
        return completeGoalInternal(userId, goalId, GoalCompletionType.MANUAL, "MANUAL", null);
    }

    @Transactional
    public GoalResponse completeGoalFromAutomation(UUID userId, UUID goalId, String sourceType, UUID sourceRefId) {
        return completeGoalInternal(userId, goalId, GoalCompletionType.AI, sourceType, sourceRefId);
    }

    /** Hedefi tamamlanmadı olarak işaretle */
    @Transactional
    public GoalResponse markNotCompleted(UUID userId, UUID goalId) {
        Goal goal = getGoal(userId, goalId);

        goal.setStatus(GoalStatus.DISMISSED);
        goal.setCompletedAt(null);
        goal.setCompletionType(null);
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
        goal.setStatus(GoalStatus.DELETED);
        goal.setDeletedAt(OffsetDateTime.now());
        goal.setCompletionType(null);
        goal.setCompletedSourceType(null);
        goal.setCompletedSourceRefId(null);
        goalRepository.save(goal);
        expirePendingSuggestions(goal.getId());

        log.info("Goal soft-deleted: goalId={}, userId={}", goalId, userId);
    }

    private GoalResponse completeGoalInternal(
            UUID userId,
            UUID goalId,
            GoalCompletionType completionType,
            String sourceType,
            UUID sourceRefId
    ) {
        Goal goal = getGoal(userId, goalId);

        goal.setStatus(GoalStatus.COMPLETED);
        goal.setCompletedAt(OffsetDateTime.now());
        goal.setCompletionType(completionType);
        goal.setCompletedSourceType(sourceType);
        goal.setCompletedSourceRefId(sourceRefId);
        goal.setDeletedAt(null);
        goalRepository.save(goal);
        expirePendingSuggestions(goal.getId());

        log.info("Goal completed: goalId={}, userId={}, sourceType={}", goalId, userId, sourceType);
        return GoalResponse.from(goal);
    }

    private Goal getGoal(UUID userId, UUID goalId) {
        return goalRepository.findByIdAndUserIdAndStatusNot(goalId, userId, GoalStatus.DELETED)
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

    private Goal findDuplicateGoal(List<Goal> openGoals, String title) {
        String normalized = normalizeTitle(title);
        return openGoals.stream()
                .filter(goal -> normalizeTitle(goal.getTitle()).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private String normalizeTitle(String value) {
        String compact = compactText(value);
        String normalized = Normalizer.normalize(compact, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String compactText(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
