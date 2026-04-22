package com.echo.repository;

import com.echo.domain.journal.JournalEntry;
import com.echo.domain.journal.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByIdAndUserId(UUID id, UUID userId);

    List<JournalEntry> findByUserIdAndEntryDateOrderByRecordedAtDesc(UUID userId, LocalDate date);

    // replaces hardcoded findTop7 with dynamic limit via Pageable
    List<JournalEntry> findByUserIdOrderByRecordedAtDesc(UUID userId, Pageable pageable);

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

    Optional<JournalEntry> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Query(value = """
           SELECT * FROM journal_entries je
           WHERE je.user_id = :userId
             AND je.status NOT IN ('complete', 'failed')
           ORDER BY je.created_at DESC
           """, nativeQuery = true)
    List<JournalEntry> findActiveByUserId(UUID userId);

    // entries stuck in non-terminal state longer than the given threshold
    @Query(value = """
           SELECT * FROM journal_entries je
           WHERE je.status IN ('uploading', 'transcribing', 'analyzing')
             AND je.created_at < :stuckBefore
           """, nativeQuery = true)
    List<JournalEntry> findStuckEntries(OffsetDateTime stuckBefore);

    @Query("""
           SELECT je.transcript
           FROM JournalEntry je
           WHERE je.user.id = :userId
             AND je.transcript IS NOT NULL
           """)
    List<String> findTranscriptsByUserId(@Param("userId") UUID userId);

    @Query(value = """
           SELECT COALESCE(SUM(array_length(regexp_split_to_array(transcript, '\\s+'), 1)), 0)
           FROM journal_entries
           WHERE user_id = :userId AND transcript IS NOT NULL AND transcript != ''
           """, nativeQuery = true)
    long countTotalWordsByUserId(@Param("userId") UUID userId);

    @Query(value = """
           SELECT COALESCE(SUM(array_length(regexp_split_to_array(transcript, '\\s+'), 1)), 0)
           FROM journal_entries
           WHERE user_id = :userId AND transcript IS NOT NULL AND transcript != ''
           """, nativeQuery = true)
    long countTotalWordsForStats(@Param("userId") UUID userId);

    @Query(value = """
           SELECT COUNT(*)
           FROM journal_entries
           WHERE user_id = :userId AND transcript IS NOT NULL AND transcript != ''
           """, nativeQuery = true)
    long countEntriesWithTranscript(@Param("userId") UUID userId);

    @Query(value = """
           SELECT je.recorded_at
           FROM journal_entries je
           WHERE je.user_id = :userId
             AND je.entry_date BETWEEN :startDate AND :endDate
             AND je.status = 'complete'
             AND je.recorded_at IS NOT NULL
           """, nativeQuery = true)
    List<java.sql.Timestamp> findRecordedAtByUserAndDateRange(@Param("userId") UUID userId,
                                                               @Param("startDate") LocalDate startDate,
                                                               @Param("endDate") LocalDate endDate);
}
