package com.echo.dto.response;

import com.echo.domain.system.SystemErrorLog;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminErrorResponse(
        OffsetDateTime occurredAt,
        Integer httpStatus,
        String errorCode,
        String exceptionClass,
        String shortMessage,
        String path,
        String method,
        String requestId,
        String userRef,
        String severity
) {
    public static AdminErrorResponse from(SystemErrorLog entry) {
        return new AdminErrorResponse(
                entry.getOccurredAt(),
                entry.getHttpStatus(),
                entry.getErrorCode(),
                entry.getExceptionClass(),
                entry.getMessage(),
                entry.getPath(),
                entry.getMethod(),
                entry.getRequestId(),
                maskUser(entry.getUserId()),
                entry.getSeverity()
        );
    }

    // Only an 8-char prefix leaves the server — enough to correlate, not to identify.
    private static String maskUser(UUID userId) {
        if (userId == null) {

            return null;
        }
        return userId.toString().substring(0, 8);
    }
}
