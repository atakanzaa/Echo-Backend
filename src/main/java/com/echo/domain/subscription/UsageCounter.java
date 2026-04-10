package com.echo.domain.subscription;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usage_counters", uniqueConstraints = {
        @UniqueConstraint(name = "uk_usage_counters_user_feature_period",
                columnNames = {"user_id", "feature_key", "period_start"})
}, indexes = {
        @Index(name = "idx_usage_counters_lookup", columnList = "user_id,feature_key,period_start")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "feature_key", nullable = false, length = 50)
    private String featureKey;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private int usageCount = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
