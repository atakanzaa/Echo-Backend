package com.echo.config;

import com.echo.ai.AISynthesisResponse;
import com.echo.domain.subscription.FeatureLimit;
import com.echo.domain.subscription.SubscriptionTier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// Caffeine in-memory cache for synthesis results.
// key format: userId:periodDays:lastEntryEpochSecond
// new journal entry or coach message changes the key, causing auto cache miss.
@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, AISynthesisResponse> synthesisCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<UUID, SubscriptionTier> entitlementCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .maximumSize(20_000)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, List<FeatureLimit>> featureLimitCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(10)
                .build();
    }

    // userId-only key (no timestamp) — survives journal/coach activity, 1-hour TTL.
    // Populated whenever any synthesis completes; consumed by AchievementService.
    @Bean
    public Cache<UUID, AISynthesisResponse> growthCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(10_000)
                .build();
    }
}
