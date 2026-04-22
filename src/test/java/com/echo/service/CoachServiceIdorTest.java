package com.echo.service;

import com.echo.ai.AIProviderRouter;
import com.echo.domain.subscription.FeatureKey;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.CoachMessageRepository;
import com.echo.repository.CoachSessionRepository;
import com.echo.repository.GoalRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoachServiceIdorTest {

    @Mock CoachSessionRepository sessionRepo;
    @Mock CoachMessageRepository messageRepo;
    @Mock UserRepository userRepo;
    @Mock AnalysisResultRepository analysisRepo;
    @Mock GoalRepository goalRepo;
    @Mock JournalEntryRepository journalEntryRepo;
    @Mock AIProviderRouter router;
    @Mock UserMemoryService userMemoryService;
    @Mock AISynthesisService synthesisService;
    @Mock EntitlementService entitlementService;
    @Mock GoalIntegrationService goalIntegrationService;
    @Mock PlatformTransactionManager transactionManager;

    @InjectMocks CoachService coachService;

    @Test
    void createSessionRejectsJournalEntryOwnedByAnotherUser() {
        UUID attackerId = UUID.randomUUID();
        UUID victimsJournalId = UUID.randomUUID();

        given(entitlementService.consumeQuota(attackerId, FeatureKey.COACH_SESSIONS)).willReturn(true);
        given(userRepo.findById(attackerId)).willReturn(Optional.of(new com.echo.domain.user.User()));
        // Victim's journal is NOT returned for (id, attackerId) — ownership scoped.
        given(journalEntryRepo.findByIdAndUserId(victimsJournalId, attackerId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> coachService.createSession(attackerId, victimsJournalId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(sessionRepo, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(messageRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
