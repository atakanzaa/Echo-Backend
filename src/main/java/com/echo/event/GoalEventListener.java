package com.echo.event;

import com.echo.service.GoalIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Triggers goal-suggestion generation and the completion-detection flow when analysis completes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalEventListener {

    private final GoalIntegrationService goalIntegrationService;

    @Async("journalProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisCompleted(JournalAnalysisCompletedEvent event) {
        try {
            goalIntegrationService.processJournalAnalysis(event.userId(), event.journalEntryId(), event.analysis());
            log.debug("Goal integration processed: userId={}, journalEntryId={}", event.userId(), event.journalEntryId());
        } catch (Exception e) {
            log.error("Goal integration failed: userId={}, journalEntryId={}, error={}",
                    event.userId(), event.journalEntryId(), e.getMessage(), e);
        }
    }
}
