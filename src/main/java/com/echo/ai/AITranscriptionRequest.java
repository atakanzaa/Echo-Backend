package com.echo.ai;

public record AITranscriptionRequest(
        byte[] audioBytes,
        String filename,
        String contentType,
        int durationSeconds
) {}
