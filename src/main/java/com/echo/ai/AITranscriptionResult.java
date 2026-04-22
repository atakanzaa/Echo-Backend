package com.echo.ai;

import java.util.List;

public record AITranscriptionResult(
        String transcript,
        String provider,
        Integer promptTokenCount,
        Integer candidateTokenCount,
        String resolvedMimeType,
        List<String> rejectionFlags
) {
    public AITranscriptionResult {
        rejectionFlags = rejectionFlags == null ? List.of() : List.copyOf(rejectionFlags);
    }

    public String cleanedTranscript() {
        return transcript == null ? "" : transcript.strip();
    }
}
