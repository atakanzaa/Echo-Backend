package com.echo.domain.subscription;

import java.util.Set;

public enum SubscriptionTier {
    FREE,
    PREMIUM;

    private static final Set<String> PREMIUM_PRODUCTS = Set.of(
            "echo_premium_monthly",
            "echo_premium_yearly"
    );

    public static SubscriptionTier fromProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            return FREE;
        }
        return PREMIUM_PRODUCTS.contains(productId) ? PREMIUM : FREE;
    }
}
