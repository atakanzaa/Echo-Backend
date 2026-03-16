package com.echo.domain.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // RBAC — V13 migration'da eklendi: USER | ADMIN
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "USER";

    // Streak & stats — AchievementService tarafından güncellenir
    @Column(name = "current_streak", nullable = false)
    @Builder.Default
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    @Builder.Default
    private int longestStreak = 0;

    @Column(name = "total_entries", nullable = false)
    @Builder.Default
    private int totalEntries = 0;

    @Column(name = "last_entry_date")
    private LocalDate lastEntryDate;

    @Column(name = "mood_score_avg", precision = 4, scale = 3)
    @Builder.Default
    private BigDecimal moodScoreAvg = BigDecimal.ZERO;

    // Optimistic locking — streak/achievement race condition'larını önler
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    // KVKK / Gizlilik onayları
    @Column(name = "ai_training_consent", nullable = false)
    @Builder.Default
    private boolean aiTrainingConsent = false;

    @Column(name = "ai_training_consent_at")
    private OffsetDateTime aiTrainingConsentAt;

    @Column(name = "kvkk_explicit_consent", nullable = false)
    @Builder.Default
    private boolean kvkkExplicitConsent = false;

    @Column(name = "kvkk_consent_at")
    private OffsetDateTime kvkkConsentAt;

    @Column(name = "privacy_policy_version", length = 10)
    private String privacyPolicyVersion;

    @Column(name = "account_deletion_requested_at")
    private OffsetDateTime accountDeletionRequestedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
