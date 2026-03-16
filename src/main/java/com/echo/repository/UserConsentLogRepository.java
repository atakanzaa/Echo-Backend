package com.echo.repository;

import com.echo.domain.user.UserConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserConsentLogRepository extends JpaRepository<UserConsentLog, UUID> {
    List<UserConsentLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
