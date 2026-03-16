package com.echo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendCoachMessageRequest(
        @NotBlank
        @Size(max = 500, message = "Mesaj en fazla 500 karakter olabilir")
        String content,
        String role
) {}
