package com.echo.event;

import com.echo.ai.AIGoal;
import com.echo.domain.goal.Goal;
import com.echo.domain.user.User;
import com.echo.repository.GoalRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Analiz tamamlandığında AI tarafından tespit edilen hedefleri kaydeder.
 * AFTER_COMMIT — ana transaction commit olduktan sonra ayrı transaction'da çalışır.
 * Per-goal try/catch — bir hedef hatası diğerlerini engellemez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalEventListener {

    private final GoalRepository  goalRepository;
    private final UserRepository   userRepository;

    @Async("journalProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisCompleted(JournalAnalysisCompletedEvent event) {
        List<AIGoal> goals = event.analysis().goals();
        if (goals == null || goals.isEmpty()) {
            log.debug("Hedef bulunamadı: journalEntryId={}", event.journalEntryId());
            return;
        }

        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            log.warn("Kullanıcı bulunamadı, hedefler kaydedilemedi: userId={}", event.userId());
            return;
        }

        log.info("Hedefler kaydediliyor: userId={}, count={}", event.userId(), goals.size());

        for (AIGoal aiGoal : goals) {
            try {
                Goal goal = Goal.builder()
                        .user(user)
                        .title(aiGoal.title())
                        .timeframe(aiGoal.timeframe())
                        .goalType(aiGoal.goalType() != null ? aiGoal.goalType() : "general")
                        .status("PENDING")
                        .sourceJournalEntryId(event.journalEntryId())
                        .detectedAt(OffsetDateTime.now())
                        .build();

                goalRepository.save(goal);
                log.debug("Hedef kaydedildi: title='{}', type={}", aiGoal.title(), aiGoal.goalType());
            } catch (Exception e) {
                log.error("Hedef kaydedilemedi: title='{}', hata={}", aiGoal.title(), e.getMessage(), e);
            }
        }

        log.info("Hedef kaydetme tamamlandı: userId={}, journalEntryId={}", event.userId(), event.journalEntryId());
    }
}
