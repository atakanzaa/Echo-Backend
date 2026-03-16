package com.echo.event;

import com.echo.domain.capsule.TimeCapsule;
import com.echo.domain.user.User;
import com.echo.repository.TimeCapsuleRepository;
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

/**
 * Analiz tamamlandığında memoryWorthy=true ise otomatik zaman kapsülü oluşturur.
 * Kilit süresi: 1 yıl. Kullanıcı sonradan silebilir.
 * AFTER_COMMIT — ana transaction commit olduktan sonra ayrı transaction'da çalışır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeCapsuleEventListener {

    private final TimeCapsuleRepository timeCapsuleRepository;
    private final UserRepository        userRepository;

    @Async("journalProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisCompleted(JournalAnalysisCompletedEvent event) {
        if (!event.analysis().memoryWorthy()) {
            return; // Sıradan giriş — kapsül oluşturma
        }

        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            log.warn("Kullanıcı bulunamadı, zaman kapsülü oluşturulamadı: userId={}", event.userId());
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
                    .sealedAt(now)
                    .unlockAt(now.plusYears(1))
                    .status("sealed")
                    .build();

            timeCapsuleRepository.save(capsule);
            log.info("Zaman kapsülü oluşturuldu: userId={}, title='{}', unlockAt={}",
                    event.userId(), title, capsule.getUnlockAt());
        } catch (Exception e) {
            log.error("Zaman kapsülü oluşturulamadı: userId={}, hata={}", event.userId(), e.getMessage(), e);
        }
    }
}
