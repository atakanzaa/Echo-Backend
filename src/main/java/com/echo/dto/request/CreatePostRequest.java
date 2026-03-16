package com.echo.dto.request;

import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @Size(max = 1000) String content,
        String contentType,
        String emoji,
        boolean isAnonymous
) {}
