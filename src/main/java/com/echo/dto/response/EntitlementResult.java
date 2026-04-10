package com.echo.dto.response;

public record EntitlementResult(
        boolean allowed,
        int used,
        int limit,
        String reason,
        String featureKey
) {
}
