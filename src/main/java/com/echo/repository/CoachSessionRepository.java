package com.echo.repository;

import com.echo.domain.coach.CoachSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachSessionRepository extends JpaRepository<CoachSession, UUID> {
    Page<CoachSession> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);
    Optional<CoachSession> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
           SELECT DISTINCT CAST(cs.started_at AS date)
           FROM coach_sessions cs
           WHERE cs.user_id = :userId
             AND cs.started_at BETWEEN :fromDate AND :toDate
           """, nativeQuery = true)
    List<java.sql.Date> findSessionDatesByUserAndRange(UUID userId, OffsetDateTime fromDate, OffsetDateTime toDate);
}
