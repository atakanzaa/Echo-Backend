package com.echo.domain.subscription;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "original_transaction_id", nullable = false, unique = true, length = 100)
    private String originalTransactionId;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "purchase_date", nullable = false)
    private OffsetDateTime purchaseDate;

    @Column(name = "expires_date", nullable = false)
    private OffsetDateTime expiresDate;

    @Column(name = "grace_period_expires_date")
    private OffsetDateTime gracePeriodExpiresDate;

    @Column(name = "auto_renew_enabled", nullable = false)
    @Builder.Default
    private boolean autoRenewEnabled = true;

    @Column(name = "auto_renew_product_id", length = 100)
    private String autoRenewProductId;

    @Column(name = "environment", nullable = false, length = 20)
    @Builder.Default
    private String environment = "Production";

    @Column(name = "latest_receipt", columnDefinition = "TEXT")
    private String latestReceipt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
