package com.echo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * iOS uygulaması Apple Speech ile cihaz üzerinde STT yaptıktan sonra
 * transkripti doğrudan gönderir. Audio sunucuya hiç gelmez.
 * UPLOADING ve TRANSCRIBING adımları atlanır, doğrudan ANALYZING'e geçilir.
 */
public record CreateJournalFromTranscriptRequest(
        @NotBlank(message = "Transkript boş olamaz")
        @Size(max = 10000, message = "Transkript en fazla 10.000 karakter olabilir")
        String transcript,

        @NotNull(message = "Kayıt tarihi zorunludur")
        String recordedAt,          // ISO-8601 offset datetime

        @Positive(message = "Süre pozitif olmalıdır")
        int durationSeconds,

        String idempotencyKey       // UUID — duplicate submission guard (optional but recommended)
) {}
