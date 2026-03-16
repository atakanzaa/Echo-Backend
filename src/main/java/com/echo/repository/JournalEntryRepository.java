package com.echo.repository;

import com.echo.domain.journal.JournalEntry;
import com.echo.domain.journal.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByIdAndUserId(UUID id, UUID userId);

    List<JournalEntry> findByUserIdAndEntryDateOrderByRecordedAtDesc(UUID userId, LocalDate date);

    List<JournalEntry> findTop7ByUserIdOrderByRecordedAtDesc(UUID userId);

    @Query(value = """
           SELECT * FROM journal_entries je
           WHERE je.user_id = :userId
             AND je.entry_date BETWEEN :startDate AND :endDate
             AND je.status = 'complete'
           ORDER BY je.entry_date DESC
           """, nativeQuery = true)
    List<JournalEntry> findCompletedByUserAndDateRange(UUID userId,
                                                        LocalDate startDate,
                                                        LocalDate endDate);

    boolean existsByUserIdAndEntryDate(UUID userId, LocalDate date);

    @Query(value = """
           SELECT * FROM journal_entries je
           WHERE je.user_id = :userId
             AND je.status NOT IN ('complete', 'failed')
           ORDER BY je.created_at DESC
           """, nativeQuery = true)
    List<JournalEntry> findActiveByUserId(UUID userId);

    /** Belirtilen süreden daha uzun süredir terminal olmayan durumda takılı entry'ler */
    @Query(value = """
           SELECT * FROM journal_entries je
           WHERE je.status IN ('uploading', 'transcribing', 'analyzing')
             AND je.created_at < :stuckBefore
           """, nativeQuery = true)
    List<JournalEntry> findStuckEntries(OffsetDateTime stuckBefore);
}
