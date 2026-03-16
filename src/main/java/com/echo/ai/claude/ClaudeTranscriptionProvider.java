package com.echo.ai.claude;

import com.echo.ai.AITranscriptionProvider;
import org.springframework.stereotype.Component;

@Component
public class ClaudeTranscriptionProvider implements AITranscriptionProvider {

    @Override
    public String transcribe(byte[] audioBytes, String filename) {
        throw new UnsupportedOperationException(
                "Claude ses transkripsiyon desteklemez. " +
                "Transkripsiyon için AI_PROVIDER=openai veya gemini kullan.");
    }
}
