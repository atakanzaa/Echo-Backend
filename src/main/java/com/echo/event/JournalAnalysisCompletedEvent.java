package com.echo.event;

import com.echo.ai.AIAnalysisResponse;

import java.util.UUID;

/**
 * Spring application event published when a journal-entry analysis completes successfully.
 * Published by JournalService; consumed by GoalEventListener and TimeCapsuleEventListener.
 *
 * Requires no Kafka/Redis — via Spring ApplicationEventPublisher it can be:
 * - listened to synchronously (same thread) or asynchronously with @Async.
 * - triggered after transaction commit via @TransactionalEventListener(AFTER_COMMIT).
 */
public record JournalAnalysisCompletedEvent(
        UUID               userId,
        UUID               journalEntryId,
        AIAnalysisResponse analysis
) {}
