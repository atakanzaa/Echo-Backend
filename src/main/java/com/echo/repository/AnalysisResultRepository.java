package com.echo.repository;

import com.echo.domain.journal.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

    Optional<AnalysisResult> findByJournalEntryId(UUID journalEntryId);

    List<AnalysisResult> findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
            UUID userId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT ar FROM AnalysisResult ar WHERE ar.user.id = :userId AND ar.entryDate = :date")
    Optional<AnalysisResult> findByUserIdAndEntryDate(UUID userId, LocalDate date);

    @Query("""
           SELECT ar.entryDate FROM AnalysisResult ar
           WHERE ar.user.id = :userId
             AND ar.entryDate BETWEEN :startDate AND :endDate
           ORDER BY ar.entryDate ASC
           """)
    List<LocalDate> findExistingDatesByUserAndRange(UUID userId,
                                                     LocalDate startDate,
                                                     LocalDate endDate);

    // latest analysis result, used for synthesis cache key
    Optional<AnalysisResult> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    // batch load for N+1 elimination in journal list endpoints
    List<AnalysisResult> findByJournalEntryIdIn(List<UUID> journalEntryIds);
}
