package com.echo.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 100)
        String displayName,
        String timezone
) {}
