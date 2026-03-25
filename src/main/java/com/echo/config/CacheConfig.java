package com.echo.config;

import com.echo.ai.AISynthesisResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                .build();
    }
}
