package com.echo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGoalRequest(
        @NotBlank(message = "Goal title is required")
        @Size(max = 200, message = "Goal title must be 200 characters or fewer")
        String title,

        @Size(max = 120, message = "Timeframe must be 120 characters or fewer")
        String timeframe
) {}
