package com.echo.config;

import com.echo.ai.AISynthesisResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Synthesis sonuçları için Caffeine in-memory cache.
 * Cache key: userId:periodDays:lastEntryEpochSecond
 * Yeni journal entry geldiğinde key değişir → otomatik cache miss.
 */
@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, AISynthesisResponse> synthesisCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }
}
