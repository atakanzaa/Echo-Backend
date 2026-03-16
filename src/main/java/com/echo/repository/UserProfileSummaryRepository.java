package com.echo.repository;

import com.echo.domain.user.UserProfileSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileSummaryRepository extends JpaRepository<UserProfileSummary, UUID> {

    Optional<UserProfileSummary> findByUserId(UUID userId);
}
