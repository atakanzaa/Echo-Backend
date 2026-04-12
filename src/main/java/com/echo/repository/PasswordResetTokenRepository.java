package com.echo.repository;

import com.echo.domain.token.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    long countByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime createdAfter);

    Optional<PasswordResetToken> findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiresAt < :now")
    void deleteAllExpired(OffsetDateTime now);
}
