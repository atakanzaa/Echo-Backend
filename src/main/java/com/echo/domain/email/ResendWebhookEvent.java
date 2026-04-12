package com.echo.domain.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "resend_webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResendWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "webhook_message_id", nullable = false, unique = true, length = 120)
    private String webhookMessageId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "email_id", length = 120)
    private String emailId;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;
}
