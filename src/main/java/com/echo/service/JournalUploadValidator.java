package com.echo.service;

import com.echo.exception.AudioValidationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JournalUploadValidator {

    private static final int MIN_DURATION_SECONDS = 3;
    private static final int MAX_DURATION_SECONDS = 600;
    private static final int MIN_BYTES_PER_SECOND = 2_000;

    public void validate(byte[] audioBytes, int durationSeconds, String contentType) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new AudioValidationException("AUDIO_EMPTY", "Audio file is empty.");
        }
        if (durationSeconds < MIN_DURATION_SECONDS || durationSeconds > MAX_DURATION_SECONDS) {
            throw new AudioValidationException(
                    "AUDIO_DURATION_INVALID",
                    "Audio duration must be between " + MIN_DURATION_SECONDS
                            + " and " + MAX_DURATION_SECONDS + " seconds.");
        }
        long bytesPerSecond = audioBytes.length / durationSeconds;
        if (bytesPerSecond < MIN_BYTES_PER_SECOND) {
            throw new AudioValidationException(
                    "AUDIO_TOO_QUIET_OR_SILENT",
                    "Ses kaydı boş ya da çok sessiz görünüyor. Mikrofon izinlerini kontrol edip tekrar dener misin?");
        }
        if (StringUtils.hasText(contentType)
                && !contentType.startsWith("audio/")
                && !contentType.equals("application/octet-stream")) {
            throw new AudioValidationException(
                    "AUDIO_CONTENT_TYPE_INVALID",
                    "Unsupported audio content type: " + contentType);
        }
    }
}
