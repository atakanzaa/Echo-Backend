package com.echo.ai.openai;

import com.echo.ai.AITranscriptionProvider;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAITranscriptionProvider implements AITranscriptionProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;

    private static final String WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions";

    @Override
    @CircuitBreaker(name = "ai-provider", fallbackMethod = "transcribeFallback")
    public String transcribe(byte[] audioBytes, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() { return filename; }
        });
        body.add("model", props.getAi().getOpenai().getTranscribeModel());
        body.add("response_format", "json");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(props.getAi().getOpenai().getApiKey());

        ResponseEntity<Map> response = restTemplate.exchange(
                WHISPER_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class
        );

        log.debug("Whisper transcription tamamlandı: {} bytes", audioBytes.length);
        return (String) response.getBody().get("text");
    }

    private String transcribeFallback(byte[] audioBytes, String filename, Throwable ex) {
        log.error("OpenAI transcription devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "Ses tanıma servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
    }
}
