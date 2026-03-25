package com.echo.ai.gemini;

import com.echo.ai.AITranscriptionProvider;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Gemini multimodal transcription: audio → text.
 * Audio is sent as base64 inline data (~20MB limit).
 * iOS m4a recordings map to audio/aac MIME type.
 */
@Slf4j
@Component
public class GeminiTranscriptionProvider implements AITranscriptionProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;
    private final GeminiClient  geminiClient;
    private final String        promptVersion;

    public GeminiTranscriptionProvider(AppProperties props,
                                       @Qualifier("transcriptionRestTemplate") RestTemplate restTemplate,
                                       GeminiClient geminiClient) {
        this.props         = props;
        this.restTemplate  = restTemplate;
        this.geminiClient  = geminiClient;
        this.promptVersion = props.getPrompts().getTranscriptionVersion();
    }

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    /** Desteklenen ses formatları → Gemini MIME türü eşlemesi */
    private static final Map<String, String> MIME_MAP = Map.of(
            "wav",  "audio/wav",
            "mp3",  "audio/mp3",
            "aac",  "audio/aac",
            "ogg",  "audio/ogg",
            "webm", "audio/webm",
            "flac", "audio/flac",
            "m4a",  "audio/aac"   // iOS recording format — AAC container
    );

    @Override
    @CircuitBreaker(name = "gemini-transcription", fallbackMethod = "transcribeFallback")
    public String transcribe(byte[] audioBytes, String filename) {
        String apiKey = props.getAi().getGemini().getApiKey();
        String model  = props.getAi().getGemini().getTranscribeModel();
        String url    = String.format(GEMINI_URL, model, apiKey);

        String mimeType = resolveMimeType(filename);
        String base64   = Base64.getEncoder().encodeToString(audioBytes);

        log.debug("Gemini transcription starting: {} bytes, mimeType={}, model={}",
                audioBytes.length, mimeType, model);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("inline_data", Map.of(
                                        "mime_type", mimeType,
                                        "data", base64
                                )),
                                Map.of("text",
                                        "Please transcribe this audio recording. " +
                                        "Return only the spoken text, no explanations or formatting.")
                        )
                ))
        );

        Map<?, ?> responseBody = geminiClient.execute(
                restTemplate, url, requestBody, "TRANSCRIPTION", promptVersion);

        String transcript = geminiClient.extractText(responseBody);
        log.debug("Gemini transcription complete: {} chars", transcript.length());
        return transcript;
    }

    private String transcribeFallback(byte[] audioBytes, String filename, Throwable ex) {
        log.error("Gemini transcription circuit open: {}", ex.getMessage());
        throw new ServiceUnavailableException("Speech recognition service is temporarily unavailable.", ex);
    }

    /** Resolves MIME type from file extension. Defaults to audio/webm for unknown formats. */
    private String resolveMimeType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "audio/webm";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return MIME_MAP.getOrDefault(ext, "audio/webm");
    }

}
