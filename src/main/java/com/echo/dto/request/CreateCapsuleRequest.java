package com.echo.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCapsuleRequest(
        String title,
        String contentText,
        @NotBlank String contentType,
        @NotBlank String unlockAt
) {}
