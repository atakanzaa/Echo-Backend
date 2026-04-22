package com.echo.ai.openai;

import com.echo.ai.AITranscriptionRequest;
import com.echo.ai.AITranscriptionResult;
import com.echo.ai.AITranscriptionProvider;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import com.echo.exception.TranscriptionFailedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class OpenAITranscriptionProvider implements AITranscriptionProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;

    public OpenAITranscriptionProvider(AppProperties props,
                                       @Qualifier("transcriptionRestTemplate") RestTemplate restTemplate) {
        this.props        = props;
        this.restTemplate = restTemplate;
    }

    private static final String WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions";

    @Override
    @CircuitBreaker(name = "openai-transcription", fallbackMethod = "transcribeFallback")
    public AITranscriptionResult transcribe(AITranscriptionRequest request) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        if (request.contentType() != null && !request.contentType().isBlank()) {
            partHeaders.setContentType(MediaType.parseMediaType(request.contentType()));
        }
        body.add("file", new HttpEntity<>(new ByteArrayResource(request.audioBytes()) {
            @Override
            public String getFilename() { return request.filename(); }
        }, partHeaders));
        body.add("model", props.getAi().getOpenai().getTranscribeModel());
        body.add("response_format", "json");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(props.getAi().getOpenai().getApiKey());

        ResponseEntity<Map> response = restTemplate.exchange(
                WHISPER_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class
        );

        String transcript = response.getBody() == null ? null : (String) response.getBody().get("text");
        if (transcript == null || transcript.isBlank()) {
            throw new TranscriptionFailedException(
                    "TRANSCRIPTION_EMPTY",
                    "Ses kaydı çözümlenemedi. Lütfen daha net bir kayıtla tekrar dene."
            );
        }

        log.debug("Whisper transcription tamamlandı: {} bytes", request.audioBytes().length);
        return new AITranscriptionResult(
                transcript.strip(),
                "openai",
                null,
                null,
                request.contentType(),
                java.util.List.of()
        );
    }

    private AITranscriptionResult transcribeFallback(AITranscriptionRequest request, Throwable ex) {
        if (ex instanceof TranscriptionFailedException transcriptionFailedException) {
            throw transcriptionFailedException;
        }
        log.error("OpenAI transcription devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "Ses tanıma servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
    }
}
