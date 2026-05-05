package com.echo.domain.capsule;

/**
 * Mirrors the PostgreSQL ENUM type {@code capsule_status}
 * defined in V7__create_time_capsules.sql as ('sealed', 'unlocked', 'opened').
 */
public enum CapsuleStatus {
    SEALED,
    UNLOCKED,
    OPENED
}
