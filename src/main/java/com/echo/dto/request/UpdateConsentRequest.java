package com.echo.dto.request;

public record UpdateConsentRequest(
        Boolean aiTrainingConsent,
        Boolean kvkkExplicitConsent
) {}
