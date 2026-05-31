package com.echo.domain.notification;

import java.util.Locale;

public enum ApnsEnvironment {
    PRODUCTION,
    SANDBOX;

    public static ApnsEnvironment fromClient(String raw) {
        if (raw == null || raw.isBlank()) {
            return PRODUCTION;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("DEVELOPMENT".equals(normalized) || "SANDBOX".equals(normalized) || "DEV".equals(normalized)) {
            return SANDBOX;
        }
        return PRODUCTION;
    }
}
