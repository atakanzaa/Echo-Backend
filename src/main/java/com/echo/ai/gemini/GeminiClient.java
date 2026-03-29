package com.echo.ai.gemini;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Shared Gemini HTTP client with retry (exponential backoff + jitter) and observability.
 * All Gemini providers delegate actual HTTP execution here.
 */
@Slf4j
@Component
public class GeminiClient {

    private static final int    MAX_ATTEMPTS  = 3;
    private static final long   BASE_DELAY_MS = 500L;
    private static final long   MAX_DELAY_MS  = 16_000L;

    /**
     * Executes a Gemini API call with retry on 429/5xx and structured logging.
     *
     * @param restTemplate per-operation RestTemplate (timeout already configured)
     * @param url          fully-formed Gemini endpoint URL
     * @param body         request body map
     * @param operation    label for logs (e.g. "JOURNAL_ANALYSIS")
     * @param promptVersion version string for log correlation
     * @return raw response body map
     */
    public Map<?, ?> execute(RestTemplate restTemplate,
                             String url,
                             Map<String, Object> body,
                             String operation,
                             String promptVersion) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            long startMs = System.currentTimeMillis();
            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.POST, entity, Map.class);

                long latencyMs = System.currentTimeMillis() - startMs;
                logSuccess(operation, promptVersion, response.getBody(), latencyMs, attempt);
                return response.getBody();

            } catch (HttpClientErrorException e) {
                long latencyMs = System.currentTimeMillis() - startMs;
                int statusCode = e.getStatusCode().value();

                if (statusCode == 429) {
                    // Rate limited — backoff and retry
                    long waitMs = resolveRetryWait(e, attempt);
                    log.warn("ai_call op={} prompt_version={} status=RATE_LIMITED attempt={} wait_ms={} latency_ms={}",
                            operation, promptVersion, attempt + 1, waitMs, latencyMs);
                    sleep(waitMs);
                    lastException = e;
                } else if (statusCode >= 500) {
                    // Server error — backoff and retry
                    long waitMs = jitteredBackoff(attempt);
                    log.warn("ai_call op={} prompt_version={} status=SERVER_ERROR http={} attempt={} wait_ms={} latency_ms={}",
                            operation, promptVersion, statusCode, attempt + 1, waitMs, latencyMs);
                    sleep(waitMs);
                    lastException = e;
                } else {
                    // 4xx client error — do NOT retry (bad request, auth, etc.)
                    log.error("ai_call op={} prompt_version={} status=CLIENT_ERROR http={} latency_ms={}",
                            operation, promptVersion, statusCode, latencyMs);
                    throw e;
                }
            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - startMs;
                if (attempt < MAX_ATTEMPTS - 1) {
                    long waitMs = jitteredBackoff(attempt);
                    log.warn("ai_call op={} prompt_version={} status=ERROR attempt={} wait_ms={} latency_ms={} error={}",
                            operation, promptVersion, attempt + 1, waitMs, latencyMs, e.getMessage());
                    sleep(waitMs);
                    lastException = e;
                } else {
                    log.error("ai_call op={} prompt_version={} status=FAILED latency_ms={}", operation, promptVersion, latencyMs);
                    throw e;
                }
            }
        }

        throw new RuntimeException("Gemini call failed after " + MAX_ATTEMPTS + " attempts", lastException);
    }

    /**
     * Logs a successful AI call with token counts, cache hit, cost estimate, and latency.
     * cached_tokens > 0 means implicit caching saved tokens (75% discount on that portion).
     */
    @SuppressWarnings("unchecked")
    private void logSuccess(String operation, String promptVersion,
                            Map<?, ?> responseBody, long latencyMs, int retryCount) {
        try {
            Map<?, ?> usage = (Map<?, ?>) responseBody.get("usageMetadata");
            if (usage == null) return;

            int inputTokens  = toInt(usage.get("promptTokenCount"));
            int outputTokens = toInt(usage.get("candidatesTokenCount"));
            Object cachedRaw  = usage.get("cachedContentTokenCount");
            int cachedTokens  = toInt(cachedRaw != null ? cachedRaw : 0);

            // Gemini 2.5 Flash pricing: $0.075/M input, $0.30/M output (cached input is 75% off)
            double effectiveInputCost = ((inputTokens - cachedTokens) * 0.075 + cachedTokens * 0.01875) / 1_000_000;
            double outputCost         = outputTokens * 0.30 / 1_000_000;
            double costUsd            = effectiveInputCost + outputCost;

            log.info("ai_call op={} prompt_version={} input_tokens={} output_tokens={} " +
                     "cached_tokens={} cache_hit={} latency_ms={} retry_count={} cost_usd={}",
                    operation, promptVersion,
                    inputTokens, outputTokens,
                    cachedTokens, cachedTokens > 0,
                    latencyMs, retryCount,
                    String.format("%.6f", costUsd));
        } catch (Exception e) {
            log.debug("Could not parse usageMetadata: {}", e.getMessage());
        }
    }

    /**
     * Exponential backoff with full jitter: delay = min(maxDelay, base * 2^attempt * rand[0.5,1.0]).
     * AWS canonical formula — prevents thundering herd on simultaneous retries.
     */
    private long jitteredBackoff(int attempt) {
        double raw = BASE_DELAY_MS * Math.pow(2, attempt) * (0.5 + Math.random() * 0.5);
        return (long) Math.min(raw, MAX_DELAY_MS);
    }

    /**
     * Honours Retry-After header from 429 responses when present.
     * Falls back to jittered exponential backoff if header is missing.
     */
    private long resolveRetryWait(HttpClientErrorException e, int attempt) {
        List<String> retryAfterHeaders = e.getResponseHeaders() != null
                ? e.getResponseHeaders().get("Retry-After")
                : null;
        if (retryAfterHeaders != null && !retryAfterHeaders.isEmpty()) {
            try {
                return Long.parseLong(retryAfterHeaders.get(0)) * 1000L;
            } catch (NumberFormatException ignored) {}
        }
        return jitteredBackoff(attempt);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    /**
     * Extracts the text content from a Gemini candidates response.
     * Handles finishReason checks for SAFETY/OTHER blocks.
     */
    @SuppressWarnings("unchecked")
    public String extractText(Map<?, ?> body) {
        List<?> candidates = (List<?>) body.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini returned empty candidates: " + body);
        }
        Map<?, ?> candidate  = (Map<?, ?>) candidates.get(0);
        Object finishReason  = candidate.get("finishReason");
        Map<?, ?> content    = (Map<?, ?>) candidate.get("content");
        if (content == null) {
            throw new RuntimeException("Gemini returned null content, finishReason=" + finishReason);
        }
        List<?> parts = (List<?>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("Gemini returned empty parts, finishReason=" + finishReason);
        }
        // Gemini 2.5 Flash (thinking model) includes thought parts with "thought": true.
        // Skip those and find the actual response part.
        String text = null;
        for (Object partObj : parts) {
            Map<?, ?> part = (Map<?, ?>) partObj;
            if (Boolean.TRUE.equals(part.get("thought"))) continue;
            text = (String) part.get("text");
            if (text != null && !text.isBlank()) break;
        }
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Gemini returned blank text, finishReason=" + finishReason);
        }
        // MAX_TOKENS means output was truncated — log loudly so it's easy to diagnose
        if ("MAX_TOKENS".equals(finishReason)) {
            log.warn("Gemini response truncated (MAX_TOKENS) — increase maxOutputTokens or disable thinking. " +
                     "text_length={}", text.length());
        }
        return text;
    }
}
