package com.echo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * iOS sends the transcript directly after on-device STT via Apple Speech.
 * Audio never reaches the server — UPLOADING and TRANSCRIBING steps are skipped,
 * entry goes straight to ANALYZING.
 */
public record CreateJournalFromTranscriptRequest(
        @NotBlank(message = "Transcript must not be blank")
        @Size(max = 10000, message = "Transcript must be 10,000 characters or fewer")
        String transcript,

        @NotNull(message = "Recorded date is required")
        String recordedAt,          // ISO-8601 offset datetime

        @Positive(message = "Duration must be positive")
        int durationSeconds,

        String idempotencyKey       // UUID — duplicate submission guard (optional but recommended)
) {}
