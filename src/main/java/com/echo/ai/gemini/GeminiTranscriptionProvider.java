package com.echo.ai.gemini;

import com.echo.ai.AITranscriptionProvider;
import com.echo.ai.AITranscriptionRequest;
import com.echo.ai.AITranscriptionResult;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import com.echo.exception.TranscriptionFailedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gemini multimodal transcription: audio -> text.
 * Audio is sent as base64 inline data (20 MB inline limit).
 */
@Slf4j
@Component
public class GeminiTranscriptionProvider implements AITranscriptionProvider {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final String TRANSCRIPTION_PROMPT =
            "Please transcribe this audio recording. Return only the spoken text, no explanations or formatting.";
    private static final int MAX_INLINE_AUDIO_BYTES = 20 * 1024 * 1024;
    private static final int MIN_PROMPT_TOKENS = 24;
    private static final double MIN_PROMPT_TOKENS_PER_SECOND = 6.0;

    private static final List<String> META_RESPONSE_MARKERS = List.of(
            "please transcribe this audio recording",
            "return only the spoken text",
            "i can't access the audio",
            "i cannot access the audio",
            "can't hear the audio",
            "cannot hear the audio",
            "unable to transcribe",
            "there is no spoken content",
            "the audio could not be transcribed",
            "ses kaydının yazıya dökülmesini talep ediyor",
            "analiz edilecek bir içerik bulunmamaktadır",
            "yalnızca konuşulan metni döndür",
            "sesi duyam",
            "ses kaydına erişem"
    );

    private static final Map<String, String> MIME_MAP = Map.of(
            "wav", "audio/wav",
            "mp3", "audio/mp3",
            "aac", "audio/aac",
            "ogg", "audio/ogg",
            "webm", "audio/webm",
            "flac", "audio/flac",
            "m4a", "audio/m4a",
            "mp4", "audio/mp4"
    );

    private final AppProperties props;
    private final RestTemplate restTemplate;
    private final GeminiClient geminiClient;
    private final String promptVersion;

    public GeminiTranscriptionProvider(AppProperties props,
                                       @Qualifier("transcriptionRestTemplate") RestTemplate restTemplate,
                                       GeminiClient geminiClient) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.geminiClient = geminiClient;
        this.promptVersion = props.getPrompts().getTranscriptionVersion();
    }

    @Override
    @CircuitBreaker(name = "gemini-transcription", fallbackMethod = "transcribeFallback")
    public AITranscriptionResult transcribe(AITranscriptionRequest request) {
        if (request.audioBytes() == null || request.audioBytes().length == 0) {
            throw new TranscriptionFailedException(
                    "TRANSCRIPTION_AUDIO_EMPTY",
                    "Ses kaydı boş görünüyor. Lütfen tekrar dene."
            );
        }
        if (request.audioBytes().length > MAX_INLINE_AUDIO_BYTES) {
            throw new TranscriptionFailedException(
                    "TRANSCRIPTION_AUDIO_TOO_LARGE",
                    "Ses kaydı mevcut sağlayıcının işleyebileceği sınırı aşıyor. Daha kısa bir kayıtla tekrar dene."
            );
        }

        String apiKey = props.getAi().getGemini().getApiKey();
        String model = props.getAi().getGemini().getTranscribeModel();
        String url = String.format(GEMINI_URL, model);
        String base64 = Base64.getEncoder().encodeToString(request.audioBytes());

        List<String> mimeCandidates = resolveMimeCandidates(request);
        List<String> attemptNotes = new ArrayList<>();
        Exception lastError = null;

        for (String mimeType : mimeCandidates) {
            long bytesPerSecond = request.durationSeconds() > 0
                    ? request.audioBytes().length / request.durationSeconds()
                    : request.audioBytes().length;
            log.info("Gemini transcription starting: bytes={} durationSeconds={} bytesPerSecond={} mimeType={} model={}",
                    request.audioBytes().length, request.durationSeconds(), bytesPerSecond, mimeType, model);

            try {
                Map<String, Object> requestBody = Map.of(
                        "contents", List.of(Map.of(
                                "parts", List.of(
                                        Map.of("inline_data", Map.of(
                                                "mime_type", mimeType,
                                                "data", base64
                                        )),
                                        Map.of("text", TRANSCRIPTION_PROMPT)
                                )
                        ))
                );

                Map<?, ?> responseBody = geminiClient.execute(
                        restTemplate, url, apiKey, requestBody, "TRANSCRIPTION", promptVersion);

                String transcript = geminiClient.extractText(responseBody).strip();
                Integer promptTokenCount = usageInt(responseBody, "promptTokenCount");
                Integer candidateTokenCount = usageInt(responseBody, "candidatesTokenCount");
                List<String> rejectionFlags = rejectFlags(request, transcript, promptTokenCount);

                if (rejectionFlags.isEmpty()) {
                    log.debug("Gemini transcription complete: {} chars", transcript.length());
                    return new AITranscriptionResult(
                            transcript,
                            "gemini",
                            promptTokenCount,
                            candidateTokenCount,
                            mimeType,
                            List.of()
                    );
                }

                String preview = preview(transcript);
                attemptNotes.add("mime=" + mimeType + " flags=" + rejectionFlags);
                log.warn("Gemini transcription rejected: mimeType={} promptTokens={} candidateTokens={} flags={} preview={}",
                        mimeType, promptTokenCount, candidateTokenCount, rejectionFlags, preview);
            } catch (Exception e) {
                lastError = e;
                attemptNotes.add("mime=" + mimeType + " error=" + e.getClass().getSimpleName());
                log.warn("Gemini transcription attempt failed: mimeType={} message={}", mimeType, e.getMessage());
            }
        }

        log.warn("Gemini transcription exhausted mime candidates: filename={} contentType={} attempts={}",
                request.filename(), request.contentType(), attemptNotes);

        if (lastError instanceof TranscriptionFailedException transcriptionFailedException) {
            throw transcriptionFailedException;
        }

        if (lastError instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }

        throw new TranscriptionFailedException(
                "TRANSCRIPTION_AUDIO_NOT_PARSED",
                "Ses kaydı çözümlenemedi. Lütfen daha net bir kayıtla tekrar dene."
        );
    }

    private AITranscriptionResult transcribeFallback(AITranscriptionRequest request, Throwable ex) {
        if (ex instanceof TranscriptionFailedException transcriptionFailedException) {
            throw transcriptionFailedException;
        }
        log.error("Gemini transcription circuit open: {}", ex.getMessage());
        throw new ServiceUnavailableException("Speech recognition service is temporarily unavailable.", ex);
    }

    private List<String> resolveMimeCandidates(AITranscriptionRequest request) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        String normalizedContentType = normalizeMimeType(request.contentType());
        if (normalizedContentType != null) {
            candidates.add(normalizedContentType);
        }

        String extensionMimeType = resolveMimeType(request.filename());
        if (extensionMimeType != null) {
            candidates.add(extensionMimeType);
        }

        boolean isM4a = (request.filename() != null && request.filename().toLowerCase(Locale.ROOT).endsWith(".m4a"))
                || "audio/m4a".equals(normalizedContentType)
                || "audio/mp4".equals(normalizedContentType);
        if (isM4a) {
            candidates.add("audio/m4a");
            candidates.add("audio/mp4");
        }

        if (candidates.isEmpty()) {
            candidates.add("audio/webm");
        }

        return List.copyOf(candidates);
    }

    private String normalizeMimeType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex).trim();
        }
        if ("audio/x-m4a".equals(normalized)) {
            return "audio/m4a";
        }
        return normalized;
    }

    private String resolveMimeType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return MIME_MAP.get(ext);
    }

    private Integer usageInt(Map<?, ?> body, String key) {
        Object usageMetadata = body.get("usageMetadata");
        if (!(usageMetadata instanceof Map<?, ?> usage)) {
            return null;
        }
        Object raw = usage.get(key);
        return raw instanceof Number number ? number.intValue() : null;
    }

    private List<String> rejectFlags(AITranscriptionRequest request,
                                     String transcript,
                                     Integer promptTokenCount) {
        List<String> flags = new ArrayList<>();

        if (promptTokenCount != null && promptTokenCount < minimumExpectedPromptTokens(request.durationSeconds())) {
            flags.add("LOW_AUDIO_TOKEN_COUNT");
        }

        String normalizedTranscript = transcript.toLowerCase(Locale.ROOT);
        if (normalizedTranscript.contains("please transcribe this audio recording")
                || normalizedTranscript.contains("return only the spoken text")) {
            flags.add("PROMPT_ECHO");
        }

        for (String marker : META_RESPONSE_MARKERS) {
            if (normalizedTranscript.contains(marker)) {
                flags.add("META_RESPONSE");
                break;
            }
        }

        return flags;
    }

    private int minimumExpectedPromptTokens(int durationSeconds) {
        return Math.max(MIN_PROMPT_TOKENS, (int) Math.ceil(durationSeconds * MIN_PROMPT_TOKENS_PER_SECOND));
    }

    private String preview(String transcript) {
        return transcript.length() <= 120 ? transcript : transcript.substring(0, 120) + "...";
    }
}
