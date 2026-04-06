package com.echo.repository;

import com.echo.domain.notification.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {
    Optional<PushToken> findByUserIdAndToken(UUID userId, String token);
    Optional<PushToken> findByToken(String token);
}
