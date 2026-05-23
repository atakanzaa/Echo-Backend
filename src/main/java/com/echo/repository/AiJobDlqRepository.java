package com.echo.repository;

import com.echo.domain.journal.AiJobDlq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AiJobDlqRepository extends JpaRepository<AiJobDlq, UUID> {

    @Query(value = """
           SELECT *
           FROM ai_job_dlq
           WHERE resolved_at IS NULL
             AND next_retry_at <= :now
             AND attempt_count < :maxAttempts
           ORDER BY next_retry_at ASC
           LIMIT :limit
           FOR UPDATE SKIP LOCKED
           """, nativeQuery = true)
    List<AiJobDlq> findRetryableJobsForUpdate(@Param("now") OffsetDateTime now,
                                              @Param("maxAttempts") int maxAttempts,
                                              @Param("limit") int limit);
}
