package com.echo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCapsuleRequest(
        @Size(max = 200, message = "Title must be 200 characters or fewer")
        String title,

        @Size(max = 5000, message = "Content must be 5,000 characters or fewer")
        String contentText,

        @NotBlank
        String contentType,

        @NotBlank
        String unlockAt
) {}
