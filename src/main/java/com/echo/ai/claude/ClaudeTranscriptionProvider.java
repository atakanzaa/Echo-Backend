package com.echo.ai.claude;

import com.echo.ai.AITranscriptionRequest;
import com.echo.ai.AITranscriptionResult;
import com.echo.ai.AITranscriptionProvider;
import com.echo.exception.TranscriptionFailedException;
import org.springframework.stereotype.Component;

@Component
public class ClaudeTranscriptionProvider implements AITranscriptionProvider {

    @Override
    public AITranscriptionResult transcribe(AITranscriptionRequest request) {
        throw new TranscriptionFailedException(
                "TRANSCRIPTION_UNSUPPORTED_PROVIDER",
                "Bu ses kaydı sağlayıcısı transkripsiyon desteklemiyor. OpenAI veya Gemini kullan."
        );
    }
}
