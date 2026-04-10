package com.echo.dto.response;

import com.echo.domain.user.UserProfileSummary;

import java.time.OffsetDateTime;

public record ProfileSummaryResponse(
        String userProfile,
        String emotionalPatterns,
        String valuesStrengths,
        String growthTrajectory,
        String weeklyDigest,
        OffsetDateTime lastUpdated
) {
    public static ProfileSummaryResponse from(UserProfileSummary p) {
        return new ProfileSummaryResponse(
                p.getUserProfile(),
                p.getEmotionalPatterns(),
                p.getValuesStrengths(),
                p.getGrowthTrajectory(),
                p.getWeeklyDigest(),
                p.getLastSynthesisAt()
        );
    }
}
