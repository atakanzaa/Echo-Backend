package com.echo.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class StorageConfig {

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    // Default RestTemplate — used by non-AI code paths and as a fallback bean
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return build(CONNECT_TIMEOUT_MS, 60_000);
    }

    // Analysis: background job, user is NOT waiting — 30s timeout
    @Bean
    @Qualifier("analysisRestTemplate")
    public RestTemplate analysisRestTemplate() {
        return build(CONNECT_TIMEOUT_MS, 30_000);
    }

    // Coach: user is actively waiting — 15s timeout, fail fast
    @Bean
    @Qualifier("coachRestTemplate")
    public RestTemplate coachRestTemplate() {
        return build(CONNECT_TIMEOUT_MS, 15_000);
    }

    // Synthesis: heaviest operation, background — 45s timeout
    @Bean
    @Qualifier("synthesisRestTemplate")
    public RestTemplate synthesisRestTemplate() {
        return build(CONNECT_TIMEOUT_MS, 45_000);
    }

    // Transcription: audio upload + model inference — 25s timeout
    @Bean
    @Qualifier("transcriptionRestTemplate")
    public RestTemplate transcriptionRestTemplate() {
        return build(CONNECT_TIMEOUT_MS, 25_000);
    }

    private RestTemplate build(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }
}
