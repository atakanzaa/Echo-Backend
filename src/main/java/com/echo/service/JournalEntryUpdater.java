package com.echo.service;

import com.echo.domain.journal.EntryStatus;
import com.echo.domain.journal.JournalEntry;
import com.echo.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Extracted from JournalService to fix @Transactional self-invocation.
// Spring AOP proxies do not intercept calls within the same bean,
// so @Transactional on private/protected methods called from @Async was dead code.
@Component
@RequiredArgsConstructor
public class JournalEntryUpdater {

    private final JournalEntryRepository journalEntryRepository;

    @Transactional
    public void updateStatus(UUID entryId, EntryStatus status) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setStatus(status);
            journalEntryRepository.save(e);
        });
    }

    @Transactional
    public void setTranscript(UUID entryId, String transcript) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setTranscript(transcript);
            journalEntryRepository.save(e);
        });
    }

    @Transactional
    public void markFailed(UUID entryId, String errorMessage) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setStatus(EntryStatus.FAILED);
            e.setErrorMessage(errorMessage);
            journalEntryRepository.save(e);
        });
    }
}
