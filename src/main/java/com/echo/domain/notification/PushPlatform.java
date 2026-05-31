package com.echo.domain.notification;

import java.util.Locale;

public enum PushPlatform {
    IOS;

    public static PushPlatform fromClient(String raw) {
        if (raw == null || raw.isBlank()) {
            return IOS;
        }
        try {
            return PushPlatform.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return IOS;
        }
    }
}
