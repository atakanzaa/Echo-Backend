package com.echo.repository;

import com.echo.domain.subscription.FeatureLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureLimitRepository extends JpaRepository<FeatureLimit, UUID> {

    List<FeatureLimit> findByTier(String tier);

    Optional<FeatureLimit> findByTierAndFeatureKey(String tier, String featureKey);
}
