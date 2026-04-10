package com.echo.dto.response;

import java.time.LocalDate;
import java.util.List;

public record QuotaStatusResponse(
        String tier,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<FeatureQuota> quotas,
        boolean adsEnabled
) {
}
