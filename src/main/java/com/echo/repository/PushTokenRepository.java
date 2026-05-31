package com.echo.repository;

import com.echo.domain.notification.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {
    Optional<PushToken> findByUserIdAndToken(UUID userId, String token);

    Optional<PushToken> findByToken(String token);

    List<PushToken> findByUserIdAndActiveTrue(UUID userId);

    @Modifying
    @Query("DELETE FROM PushToken t " +
            "WHERE t.active = false " +
            "AND (t.lastUsedAt IS NULL OR t.lastUsedAt < :cutoff) " +
            "AND t.updatedAt < :cutoff")
    int deleteInactiveOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
