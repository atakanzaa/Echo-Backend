package com.echo.event;

import com.echo.ai.AIAnalysisResponse;

import java.util.UUID;

/**
 * Günlük giriş analizi başarıyla tamamlandığında yayınlanan Spring uygulama eventi.
 * JournalService → GoalEventListener, TimeCapsuleEventListener dinler.
 *
 * Kafka/Redis gerektirmez — Spring ApplicationEventPublisher ile:
 * - Senkron (aynı thread) veya @Async ile asenkron dinlenebilir.
 * - @TransactionalEventListener(AFTER_COMMIT) ile transaction commit sonrası tetiklenir.
 */
public record JournalAnalysisCompletedEvent(
        UUID               userId,
        UUID               journalEntryId,
        AIAnalysisResponse analysis
) {}
