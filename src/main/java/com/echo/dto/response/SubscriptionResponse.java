package com.echo.dto.response;

import java.time.OffsetDateTime;

public record SubscriptionResponse(
        String tier,
        String status,
        String productId,
        String originalTransactionId,
        OffsetDateTime purchaseDate,
        OffsetDateTime expiresDate,
        boolean autoRenewEnabled,
        boolean adsEnabled
) {
}
