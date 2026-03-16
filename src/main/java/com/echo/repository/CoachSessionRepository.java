package com.echo.repository;

import com.echo.domain.coach.CoachSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachSessionRepository extends JpaRepository<CoachSession, UUID> {
    List<CoachSession> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<CoachSession> findByIdAndUserId(UUID id, UUID userId);
}
