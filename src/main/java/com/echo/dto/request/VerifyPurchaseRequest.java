package com.echo.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyPurchaseRequest(
        @NotBlank(message = "signedTransaction is required")
        String signedTransaction
) {
}
