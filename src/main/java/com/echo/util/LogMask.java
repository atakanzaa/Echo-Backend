package com.echo.util;

import org.springframework.util.StringUtils;

/**
 * Utilities for masking PII before it is written to logs.
 * Use these helpers whenever an email or other identifier needs to appear in a
 * log statement for debuggability without leaking the raw value.
 */
public final class LogMask {

    private LogMask() {
    }

    /**
     * Masks an email by keeping the first character of the local part and the
     * domain, e.g. {@code user@example.com} → {@code u***@example.com}.
     * Returns {@code null} or {@code ""} unchanged so logs do not mistake them
     * for a real masked address.
     */
    public static String email(String email) {
        if (!StringUtils.hasText(email)) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
