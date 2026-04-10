package com.echo.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RestorePurchaseRequest(
        @NotBlank(message = "signedTransaction is required")
        String signedTransaction
) {
}
