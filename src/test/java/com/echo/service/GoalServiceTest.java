package com.echo.service;

import com.echo.domain.goal.Goal;
import com.echo.domain.goal.GoalCompletionType;
import com.echo.domain.goal.GoalCreationType;
import com.echo.domain.goal.GoalStatus;
import com.echo.domain.subscription.FeatureKey;
import com.echo.domain.user.User;
import com.echo.dto.request.CreateGoalRequest;
import com.echo.dto.response.GoalResponse;
import com.echo.repository.GoalRepository;
import com.echo.repository.GoalSuggestionRepository;
import com.echo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock GoalRepository goalRepository;
    @Mock GoalSuggestionRepository goalSuggestionRepository;
    @Mock UserRepository userRepository;
    @Mock EntitlementService entitlementService;

    @InjectMocks GoalService goalService;

    @Test
    void createManualGoal_setsManualCreationType() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@echo.com").timezone("UTC").build();

        given(goalRepository.findByUserIdAndStatusInOrderByDetectedAtDesc(userId, GoalStatus.openStatuses()))
                .willReturn(List.of());
        given(entitlementService.getLimit(userId, FeatureKey.ACTIVE_GOALS)).willReturn(-1);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(goalRepository.save(any(Goal.class))).willAnswer(invocation -> {
            Goal goal = invocation.getArgument(0);
            goal.setId(UUID.randomUUID());
            return goal;
        });

        GoalResponse response = goalService.createManualGoal(
                userId,
                new CreateGoalRequest("Finish launch checklist", "Tonight")
        );

        assertThat(response.creationType()).isEqualTo(GoalCreationType.MANUAL);
        assertThat(response.title()).isEqualTo("Finish launch checklist");
        assertThat(response.timeframe()).isEqualTo("Tonight");
    }

    @Test
    void completeGoal_setsManualCompletionType() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        Goal goal = Goal.builder()
                .id(goalId)
                .user(User.builder().id(userId).email("test@echo.com").timezone("UTC").build())
                .title("Run 5 km")
                .creationType(GoalCreationType.AI)
                .status(GoalStatus.PENDING)
                .detectedAt(OffsetDateTime.now())
                .build();

        given(goalRepository.findByIdAndUserIdAndStatusNot(goalId, userId, GoalStatus.DELETED))
                .willReturn(Optional.of(goal));
        given(goalSuggestionRepository.findByGoalIdAndStatus(goalId, "PENDING")).willReturn(List.of());

        GoalResponse response = goalService.completeGoal(userId, goalId);

        assertThat(response.completionType()).isEqualTo(GoalCompletionType.MANUAL);
        assertThat(goal.getCompletedSourceType()).isEqualTo("MANUAL");
        then(goalRepository).should().save(goal);
    }

    @Test
    void completeGoalFromAutomation_setsAiCompletionType() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID sourceRefId = UUID.randomUUID();
        Goal goal = Goal.builder()
                .id(goalId)
                .user(User.builder().id(userId).email("test@echo.com").timezone("UTC").build())
                .title("Fix the onboarding bug")
                .creationType(GoalCreationType.MANUAL)
                .status(GoalStatus.PENDING)
                .detectedAt(OffsetDateTime.now())
                .build();

        given(goalRepository.findByIdAndUserIdAndStatusNot(goalId, userId, GoalStatus.DELETED))
                .willReturn(Optional.of(goal));
        given(goalSuggestionRepository.findByGoalIdAndStatus(goalId, "PENDING")).willReturn(List.of());

        GoalResponse response = goalService.completeGoalFromAutomation(userId, goalId, "JOURNAL", sourceRefId);

        assertThat(response.completionType()).isEqualTo(GoalCompletionType.AI);
        assertThat(goal.getCompletedSourceType()).isEqualTo("JOURNAL");
        assertThat(goal.getCompletedSourceRefId()).isEqualTo(sourceRefId);
    }
}
