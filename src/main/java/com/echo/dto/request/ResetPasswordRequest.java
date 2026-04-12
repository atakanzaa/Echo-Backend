package com.echo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email
        String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "Reset code must be 6 digits")
        String code,

        @NotBlank
        @Size(min = 8, max = 100)
        String newPassword
) {}
