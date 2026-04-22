package com.echo.repository;

import com.echo.domain.coach.CoachSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachSessionRepository extends JpaRepository<CoachSession, UUID> {
    Page<CoachSession> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);
    Optional<CoachSession> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CoachSession cs WHERE cs.id = :id AND cs.user.id = :userId")
    Optional<CoachSession> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query(value = """
           SELECT DISTINCT CAST(cs.started_at AS date)
           FROM coach_sessions cs
           WHERE cs.user_id = :userId
             AND cs.started_at BETWEEN :fromDate AND :toDate
           """, nativeQuery = true)
    List<java.sql.Date> findSessionDatesByUserAndRange(UUID userId, OffsetDateTime fromDate, OffsetDateTime toDate);
}
