package com.echo.repository;

import com.echo.domain.coach.CoachMessage;
import com.echo.domain.coach.MessageRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachMessageRepository extends JpaRepository<CoachMessage, UUID> {
    List<CoachMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Page<CoachMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId, Pageable pageable);

    // messages after a given date, used for synthesis coach context
    List<CoachMessage> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID userId, OffsetDateTime since);

    // latest coach message, included in synthesis cache key
    Optional<CoachMessage> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    // bulk delete all messages for a session (avoids N+1 load-then-delete)
    @Modifying
    @Query("DELETE FROM CoachMessage m WHERE m.session.id = :sessionId")
    void deleteBySessionId(UUID sessionId);

    long countBySessionIdAndRole(UUID sessionId, MessageRole role);
}
