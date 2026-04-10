package com.echo.repository;

import com.echo.domain.subscription.SubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {

    List<SubscriptionEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
