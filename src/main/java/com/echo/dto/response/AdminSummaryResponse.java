package com.echo.dto.response;

import java.time.OffsetDateTime;

public record AdminSummaryResponse(
        long totalUsers,
        long newUsers24h,
        long newUsers7d,
        long activeToday,
        long totalEntries,
        long entries24h,
        long stuckEntries,
        long failedEntries,
        long pushSent24h,
        long pushFailed24h,
        long pushSkipped24h,
        long pushPending,
        long activePushTokens,
        long errors24h,
        long dlqUnresolved,
        long activeSubscriptions,
        String appVersion,
        boolean dbReachable,
        OffsetDateTime generatedAt
) {
}
