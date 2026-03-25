package com.echo.repository;

import com.echo.domain.coach.CoachSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CoachSessionRepository extends JpaRepository<CoachSession, UUID> {
    Page<CoachSession> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);
    Optional<CoachSession> findByIdAndUserId(UUID id, UUID userId);
}
