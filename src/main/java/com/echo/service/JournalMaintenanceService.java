package com.echo.service;

import com.echo.domain.journal.EntryStatus;
import com.echo.domain.journal.JournalEntry;
import com.echo.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalMaintenanceService {

    private static final int  STUCK_THRESHOLD_MINUTES = 20;
    private static final long CHECK_INTERVAL_MS        = 5 * 60 * 1_000L; // 5 dakika

    private final JournalEntryRepository journalEntryRepository;

    @Scheduled(fixedDelay = CHECK_INTERVAL_MS, initialDelay = 60_000L)
    @Transactional
    public void recoverStuckEntries() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<JournalEntry> stuck = journalEntryRepository.findStuckEntries(threshold);

        if (stuck.isEmpty()) return;

        log.warn("Takılı {} entry tespit edildi, FAILED olarak işaretleniyor", stuck.size());

        for (JournalEntry entry : stuck) {
            log.warn("Takılı entry recover: id={}, status={}, createdAt={}",
                    entry.getId(), entry.getStatus(), entry.getCreatedAt());

            EntryStatus previousStatus = entry.getStatus();
            entry.setStatus(EntryStatus.FAILED);
            entry.setErrorMessage(
                    "İşlem zaman aşımına uğradı (durum: " + previousStatus.name() +
                    "). Lütfen kaydı tekrar yükleyin."
            );
            journalEntryRepository.save(entry);
        }
    }
}
