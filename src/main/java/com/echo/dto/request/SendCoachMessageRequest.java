package com.echo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendCoachMessageRequest(
        @NotBlank
        @Size(max = 500, message = "Message must be 500 characters or fewer")
        String content,
        String role
) {}
