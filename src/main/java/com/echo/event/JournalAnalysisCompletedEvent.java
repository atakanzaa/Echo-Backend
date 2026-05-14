package com.echo.event;

import com.echo.ai.AIAnalysisResponse;

import java.util.UUID;

/**
 * Spring application event published after a journal analysis completes successfully.
 * Listeners: GoalEventListener, TimeCapsuleEventListener, NotificationEventListener.
 *
 * No external broker required — Spring ApplicationEventPublisher:
 * - May be handled synchronously or with @Async.
 * - @TransactionalEventListener(AFTER_COMMIT) fires after the publishing transaction commits.
 */
public record JournalAnalysisCompletedEvent(
        UUID               userId,
        UUID               journalEntryId,
        AIAnalysisResponse analysis
) {}
