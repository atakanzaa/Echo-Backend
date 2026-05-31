package com.echo.service.notification;

public record ApnsPushResult(
        boolean accepted,
        boolean tokenInvalid,
        String apnsId,
        String errorCode,
        String errorReason
) {
    public static ApnsPushResult accepted(String apnsId) {
        return new ApnsPushResult(true, false, apnsId, null, null);
    }

    public static ApnsPushResult rejected(String errorCode, String errorReason, boolean tokenInvalid) {
        return new ApnsPushResult(false, tokenInvalid, null, errorCode, errorReason);
    }
}
