package com.echo.dto.response;

public record AuthResponse(
        String       accessToken,
        String       refreshToken,
        long         expiresIn,
        UserResponse user
) {}
