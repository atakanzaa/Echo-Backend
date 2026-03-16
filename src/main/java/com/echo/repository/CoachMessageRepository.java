package com.echo.repository;

import com.echo.domain.coach.CoachMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachMessageRepository extends JpaRepository<CoachMessage, UUID> {
    List<CoachMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /** Belirli tarihten sonraki mesajlar — synthesis için coach context toplamada kullanılır */
    List<CoachMessage> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID userId, OffsetDateTime since);

    /** Son coach mesajı — synthesis cache key'ine dahil edilir (coach-only konuşmalar cache miss tetikler) */
    Optional<CoachMessage> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}
