package com.echo.event;

import com.echo.domain.capsule.CapsuleStatus;
import com.echo.domain.capsule.TimeCapsule;
import com.echo.domain.subscription.FeatureKey;
import com.echo.domain.user.User;
import com.echo.repository.TimeCapsuleRepository;
import com.echo.repository.UserRepository;
import com.echo.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;

/**
 * Creates an automatic time capsule when an analysis is memoryWorthy=true.
 * Lock duration: 1 year. The user can delete it later.
 * AFTER_COMMIT — runs in a separate transaction after the main one commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeCapsuleEventListener {

    private final TimeCapsuleRepository timeCapsuleRepository;
    private final UserRepository        userRepository;
    private final EntitlementService entitlementService;

    @Async("journalProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisCompleted(JournalAnalysisCompletedEvent event) {
        if (!event.analysis().memoryWorthy()) {
            return; // ordinary entry — no capsule needed
        }

        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            log.warn("Time capsule creation skipped, user not found: userId={}", event.userId());
            return;
        }

        if (timeCapsuleRepository.existsByUserIdAndSourceJournalEntryId(user.getId(), event.journalEntryId())) {
            log.debug("Time capsule already exists for this entry, skipping");
            return;
        }

        int limit = entitlementService.getLimit(user.getId(), FeatureKey.ACTIVE_TIME_CAPSULES);
        int activeCapsules = timeCapsuleRepository.countByUserIdAndStatus(user.getId(), CapsuleStatus.SEALED);
        if (limit != -1 && activeCapsules >= limit) {
            log.info("Time capsule limit reached, skipping auto capsule creation: userId={}, limit={}",
                    user.getId(), limit);
            return;
        }

        String title = event.analysis().memoryCapsuleTitle();
        if (title == null || title.isBlank()) {
            title = "Anıya Değer Gün — " + OffsetDateTime.now().toLocalDate();
        }

        String content = event.analysis().summary();
        OffsetDateTime now = OffsetDateTime.now();

        try {
            TimeCapsule capsule = TimeCapsule.builder()
                    .user(user)
                    .title(title)
                    .contentText(content)
                    .contentType("text")
                    .sourceJournalEntryId(event.journalEntryId())
                    .sealedAt(now)
                    .unlockAt(now.plusYears(1))
                    .status(CapsuleStatus.SEALED)
                    .build();

            timeCapsuleRepository.save(capsule);
            log.info("Time capsule created: userId={}, capsuleId={}, unlockAt={}",
                    event.userId(), capsule.getId(), capsule.getUnlockAt());
        } catch (Exception e) {
            log.error("Time capsule creation failed: userId={}", event.userId(), e);
        }
    }
}
