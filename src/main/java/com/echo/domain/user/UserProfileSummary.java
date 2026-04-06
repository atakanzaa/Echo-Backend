package com.echo.domain.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_profile_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // raw FK column — set by UserMemoryService.getOrCreate()
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // read-only relation — FK managed via userId column
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "weekly_digest", columnDefinition = "TEXT")
    private String weeklyDigest;

    @Column(name = "user_profile", columnDefinition = "TEXT")
    private String userProfile;

    @Column(name = "emotional_patterns", columnDefinition = "TEXT")
    private String emotionalPatterns;

    @Column(name = "values_strengths", columnDefinition = "TEXT")
    private String valuesStrengths;

    @Column(name = "growth_trajectory", columnDefinition = "TEXT")
    private String growthTrajectory;

    @Column(name = "last_synthesis_at")
    private OffsetDateTime lastSynthesisAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
