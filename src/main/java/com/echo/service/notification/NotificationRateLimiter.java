package com.echo.service.notification;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user push rate limit (5 pushes per rolling hour). Prevents bursts —
 * a flood of community likes, scheduled reminders firing alongside an event,
 * or a buggy caller — from spamming the lock screen.
 *
 * Bucket store is in-process. Acceptable for a single-VPS deployment.
 */
@Component
public class NotificationRateLimiter {

    private static final long MAX_PER_HOUR = 5;

    private final ConcurrentHashMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean shouldThrottle(UUID userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, id -> Bucket.builder()
                .addLimit(Bandwidth.simple(MAX_PER_HOUR, Duration.ofHours(1)))
                .build());
        return !bucket.tryConsume(1);
    }
}
