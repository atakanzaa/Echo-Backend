package com.echo.service;

import com.echo.domain.coach.MessageRole;
import com.echo.domain.subscription.FeatureKey;
import com.echo.domain.subscription.FeatureLimit;
import com.echo.domain.subscription.SubscriptionStatus;
import com.echo.domain.subscription.SubscriptionTier;
import com.echo.domain.subscription.UsageCounter;
import com.echo.dto.response.FeatureQuota;
import com.echo.dto.response.QuotaStatusResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.CoachMessageRepository;
import com.echo.repository.FeatureLimitRepository;
import com.echo.repository.SubscriptionRepository;
import com.echo.repository.UsageCounterRepository;
import com.echo.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final FeatureLimitRepository featureLimitRepository;
    private final UsageCounterRepository usageCounterRepository;
    private final UserRepository userRepository;
    private final CoachMessageRepository coachMessageRepository;
    private final Cache<UUID, SubscriptionTier> entitlementCache;
    private final Cache<String, List<FeatureLimit>> featureLimitCache;

    public SubscriptionTier getUserTier(UUID userId) {
        SubscriptionTier cached = entitlementCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        SubscriptionTier resolved = subscriptionRepository.findByUserId(userId)
                .filter(s -> s.getStatus() != null && s.getStatus().grantsAccess())
                .filter(s -> s.getExpiresDate() == null
                        || s.getExpiresDate().isAfter(OffsetDateTime.now())
                        || s.getStatus() == SubscriptionStatus.GRACE_PERIOD
                        || s.getStatus() == SubscriptionStatus.BILLING_RETRY)
                .map(s -> SubscriptionTier.PREMIUM)
                .orElse(SubscriptionTier.FREE);

        entitlementCache.put(userId, resolved);
        return resolved;
    }

    @Transactional
    public boolean consumeQuota(UUID userId, FeatureKey feature) {
        int limit = getLimit(userId, feature);
        if (limit == -1) {
            return true;
        }

        LocalDate periodStart = currentPeriodStart();
        UsageCounter counter = usageCounterRepository
                .findForUpdate(userId, feature.name(), periodStart)
                .orElseGet(() -> createCounter(userId, feature, periodStart));

        if (counter.getUsageCount() >= limit) {
            return false;
        }

        counter.setUsageCount(counter.getUsageCount() + 1);
        usageCounterRepository.save(counter);
        return true;
    }

    @Transactional
    public void refundQuota(UUID userId, FeatureKey feature) {
        int limit = getLimit(userId, feature);
        if (limit == -1) {
            return;
        }

        LocalDate periodStart = currentPeriodStart();
        usageCounterRepository.findForUpdate(userId, feature.name(), periodStart)
                .ifPresent(counter -> {
                    counter.setUsageCount(Math.max(0, counter.getUsageCount() - 1));
                    usageCounterRepository.save(counter);
                });
    }

    @Transactional(readOnly = true)
    public boolean consumeSessionQuota(UUID userId, UUID sessionId, FeatureKey feature) {
        return hasSessionQuota(userId, sessionId, feature);
    }

    @Transactional(readOnly = true)
    public boolean hasSessionQuota(UUID userId, UUID sessionId, FeatureKey feature) {
        int limit = getLimit(userId, feature);
        if (limit == -1) {
            return true;
        }

        long usedInSession = coachMessageRepository.countBySessionIdAndRole(sessionId, MessageRole.USER);
        return usedInSession < limit;
    }

    @Transactional(readOnly = true)
    public int getLimit(UUID userId, FeatureKey feature) {
        SubscriptionTier tier = getUserTier(userId);
        return resolveLimit(tier, feature);
    }

    @Transactional(readOnly = true)
    public QuotaStatusResponse getQuotaStatus(UUID userId) {
        SubscriptionTier tier = getUserTier(userId);
        LocalDate periodStart = currentPeriodStart();
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

        List<FeatureLimit> limits = getLimitsForTier(tier.name());
        Map<String, Integer> usageByFeature = usageCounterRepository.findByUserIdAndPeriodStart(userId, periodStart)
                .stream()
                .collect(Collectors.toMap(UsageCounter::getFeatureKey, UsageCounter::getUsageCount));

        List<FeatureQuota> quotas = limits.stream()
                .sorted(Comparator.comparing(FeatureLimit::getFeatureKey))
                .map(limit -> new FeatureQuota(
                        limit.getFeatureKey(),
                        usageByFeature.getOrDefault(limit.getFeatureKey(), 0),
                        limit.getMaxValue(),
                        limit.getMaxValue() == -1
                ))
                .toList();

        boolean adsEnabled = userRepository.findById(userId)
                .map(u -> u.isAdsEnabled())
                .orElse(true);

        return new QuotaStatusResponse(
                tier.name(),
                periodStart,
                periodEnd,
                quotas,
                adsEnabled
        );
    }

    public void invalidateCache(UUID userId) {
        entitlementCache.invalidate(userId);
    }

    private UsageCounter createCounter(UUID userId, FeatureKey feature, LocalDate periodStart) {
        UsageCounter newCounter = UsageCounter.builder()
                .user(userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found")))
                .featureKey(feature.name())
                .periodStart(periodStart)
                .usageCount(0)
                .build();

        try {
            return usageCounterRepository.saveAndFlush(newCounter);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent insert race for first usage in period: lock-and-reload winner row.
            return usageCounterRepository.findForUpdate(userId, feature.name(), periodStart)
                    .orElseThrow(() -> ex);
        }
    }

    private int resolveLimit(SubscriptionTier tier, FeatureKey feature) {
        Map<String, FeatureLimit> byFeature = getLimitsForTier(tier.name()).stream()
                .collect(Collectors.toMap(FeatureLimit::getFeatureKey, Function.identity()));

        FeatureLimit limit = byFeature.get(feature.name());
        if (limit == null) {
            throw new IllegalStateException("Missing feature limit for tier=" + tier + " feature=" + feature);
        }
        return limit.getMaxValue();
    }

    private List<FeatureLimit> getLimitsForTier(String tier) {
        List<FeatureLimit> cached = featureLimitCache.getIfPresent(tier);
        if (cached != null) {
            return cached;
        }

        List<FeatureLimit> limits = featureLimitRepository.findByTier(tier);
        if (limits.isEmpty()) {
            throw new IllegalStateException("No feature limits configured for tier=" + tier);
        }

        featureLimitCache.put(tier, limits);
        return limits;
    }

    private LocalDate currentPeriodStart() {
        return LocalDate.now().withDayOfMonth(1);
    }
}
