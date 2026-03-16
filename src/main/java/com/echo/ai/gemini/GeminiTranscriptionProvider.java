package com.echo.ai.gemini;

import com.echo.ai.AITranscriptionProvider;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Gemini multimodal API ile ses → metin transkripsiyon.
 * Base64 kodlanmış ses verisi inline olarak gönderilir (~20MB sınır dahilinde).
 * iOS ses kayıtları için m4a → audio/aac, diğer formatlar otomatik algılanır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiTranscriptionProvider implements AITranscriptionProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;

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
            "m4a",  "audio/aac"   // iOS kayıt formatı — AAC container
    );

    @Override
    @CircuitBreaker(name = "ai-provider", fallbackMethod = "transcribeFallback")
    public String transcribe(byte[] audioBytes, String filename) {
        String apiKey = props.getAi().getGemini().getApiKey();
        String model  = props.getAi().getGemini().getTranscribeModel();
        String url    = String.format(GEMINI_URL, model, apiKey);

        String mimeType = resolveMimeType(filename);
        String base64   = Base64.getEncoder().encodeToString(audioBytes);

        log.debug("Gemini transkripsiyon başlıyor: {} bytes, mimeType={}, model={}",
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        String transcript = extractGeminiContent(response.getBody());
        log.debug("Gemini transkripsiyon tamamlandı: {} karakter", transcript.length());
        return transcript;
    }

    private String transcribeFallback(byte[] audioBytes, String filename, Throwable ex) {
        log.error("Gemini transkripsiyon devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "Ses tanıma servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
    }

    /**
     * Dosya uzantısından MIME türünü çözer.
     * Bilinmeyen format için audio/webm varsayılan — iOS WebRTC çıktısı.
     */
    private String resolveMimeType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "audio/webm";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return MIME_MAP.getOrDefault(ext, "audio/webm");
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiContent(Map<?, ?> body) {
        List<?> candidates = (List<?>) body.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini boş candidates döndürdü: " + body);
        }
        Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
        Map<?, ?> content   = (Map<?, ?>) candidate.get("content");
        List<?> parts       = (List<?>) content.get("parts");
        Map<?, ?> part      = (Map<?, ?>) parts.get(0);
        return (String) part.get("text");
    }
}
