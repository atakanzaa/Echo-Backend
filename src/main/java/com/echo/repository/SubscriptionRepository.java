package com.echo.repository;

import com.echo.domain.subscription.Subscription;
import com.echo.domain.subscription.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    Optional<Subscription> findByOriginalTransactionId(String originalTransactionId);

    List<Subscription> findByStatusInAndExpiresDateBefore(List<SubscriptionStatus> statuses, OffsetDateTime date);
}
