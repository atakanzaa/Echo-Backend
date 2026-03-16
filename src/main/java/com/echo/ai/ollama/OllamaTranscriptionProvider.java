package com.echo.ai.ollama;

import com.echo.ai.AITranscriptionProvider;
import org.springframework.stereotype.Component;

/**
 * Ollama STT desteği yok. Transkripsiyon cihazda (Apple Speech) yapılır.
 * Transcript-only endpoint ile audio sunucuya gelmez.
 */
@Component
public class OllamaTranscriptionProvider implements AITranscriptionProvider {

    @Override
    public String transcribe(byte[] audioBytes, String filename) {
        throw new UnsupportedOperationException(
                "Ollama ses transkripsiyonu desteklemez. " +
                "İOS uygulamasında Apple Speech kullanın ve /transcript endpoint'ini tercih edin."
        );
    }
}
