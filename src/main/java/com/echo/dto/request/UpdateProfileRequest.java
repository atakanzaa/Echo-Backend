package com.echo.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 100)
        String displayName,
        String timezone,
        @Pattern(regexp = "^(en|tr)$", message = "Language must be 'en' or 'tr'")
        String language
) {}
