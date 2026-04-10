package com.echo.dto.response;

public record FeatureQuota(
        String feature,
        int used,
        int limit,
        boolean unlimited
) {
}
