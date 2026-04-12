package com.echo.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseConfirmedEvent(
        UUID userId,
        String email,
        String language,
        String productId,
        OffsetDateTime activatedAt
) {}
