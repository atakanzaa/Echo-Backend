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

/**
 * Prodüksiyon critical bug: Sunucu restart olduğunda async pipeline'daki entry'ler
 * UPLOADING / TRANSCRIBING / ANALYZING durumunda sonsuza dek takılı kalır.
 * Bu servis her 5 dakikada çalışır ve 10 dakikadan fazla süredir bu durumlarda
 * bulunan entry'leri FAILED olarak işaretler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalMaintenanceService {

    private static final int  STUCK_THRESHOLD_MINUTES = 10;
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

            entry.setStatus(EntryStatus.FAILED);
            entry.setErrorMessage(
                    "İşlem zaman aşımına uğradı (durum: " + entry.getStatus().name() +
                    "). Lütfen kaydı tekrar yükleyin."
            );
            journalEntryRepository.save(entry);
        }
    }
}
