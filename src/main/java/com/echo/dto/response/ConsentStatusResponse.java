package com.echo.dto.response;

import com.echo.domain.user.User;

import java.time.OffsetDateTime;

public record ConsentStatusResponse(
        boolean aiTrainingConsent,
        OffsetDateTime aiTrainingConsentAt,
        boolean kvkkExplicitConsent,
        OffsetDateTime kvkkConsentAt,
        String privacyPolicyVersion
) {
    public static ConsentStatusResponse from(User user) {
        return new ConsentStatusResponse(
                user.isAiTrainingConsent(),
                user.getAiTrainingConsentAt(),
                user.isKvkkExplicitConsent(),
                user.getKvkkConsentAt(),
                user.getPrivacyPolicyVersion()
        );
    }
}
