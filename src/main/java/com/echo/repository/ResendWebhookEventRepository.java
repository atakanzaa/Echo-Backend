package com.echo.repository;

import com.echo.domain.email.ResendWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResendWebhookEventRepository extends JpaRepository<ResendWebhookEvent, UUID> {

    boolean existsByWebhookMessageId(String webhookMessageId);
}
