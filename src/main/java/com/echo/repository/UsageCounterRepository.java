package com.echo.repository;

import com.echo.domain.subscription.UsageCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsageCounterRepository extends JpaRepository<UsageCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT uc
            FROM UsageCounter uc
            WHERE uc.user.id = :userId
              AND uc.featureKey = :featureKey
              AND uc.periodStart = :periodStart
            """)
    Optional<UsageCounter> findForUpdate(@Param("userId") UUID userId,
                                         @Param("featureKey") String featureKey,
                                         @Param("periodStart") LocalDate periodStart);

    Optional<UsageCounter> findByUserIdAndFeatureKeyAndPeriodStart(UUID userId,
                                                                    String featureKey,
                                                                    LocalDate periodStart);

    List<UsageCounter> findByUserIdAndPeriodStart(UUID userId, LocalDate periodStart);
}
