package com.echo.domain.subscription;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "feature_limits", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feature_limits_tier_feature", columnNames = {"tier", "feature_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "feature_key", nullable = false, length = 50)
    private String featureKey;

    @Column(name = "max_value", nullable = false)
    private int maxValue;

    @Column(name = "period", length = 20)
    private String period;
}
