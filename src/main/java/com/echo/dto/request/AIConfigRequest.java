package com.echo.dto.request;
import jakarta.validation.constraints.NotBlank;
public record AIConfigRequest(@NotBlank String provider) {}
