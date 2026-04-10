package com.echo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank
        @JsonProperty("id_token")
        String idToken,

        String timezone,

        String language
) {}
