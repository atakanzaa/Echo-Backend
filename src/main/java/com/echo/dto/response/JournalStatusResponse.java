package com.echo.dto.response;

import com.echo.domain.journal.JournalEntry;

import java.util.UUID;

public record JournalStatusResponse(
        UUID   id,
        String status,
        String errorMessage
) {
    public static JournalStatusResponse from(JournalEntry entry) {
        return new JournalStatusResponse(
                entry.getId(),
                entry.getStatus().name().toLowerCase(),
                entry.getErrorMessage()
        );
    }
}
