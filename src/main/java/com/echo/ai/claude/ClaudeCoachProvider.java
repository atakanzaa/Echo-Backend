package com.echo.ai.claude;

import com.echo.ai.AICoachProvider;
import com.echo.ai.AICoachRequest;
import com.echo.ai.AICoachResponse;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeCoachProvider implements AICoachProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;

    private static final String CLAUDE_URL    = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VER = "2023-06-01";

    private static final String BASE_PROMPT = """
            Sen Echo, kişisel gelişim odaklı empatik bir AI yansıma koçusun.
            Uzmanlık: zihinsel sağlık, yaşam koçluğu, kariyer.
            ÇERÇEVE: Kabul Et → Yansıt → Keşfet → Bağla → Güçlendir.
            SINIRLAR: Tıbbi, hukuki, finansal, konu dışı sorulara girmeyeceksin.
            GÜVENLİK: Kriz ifadelerinde 182 (Ruh Sağlığı Hattı) paylaş.
            KURALLAR: 2-4 cümle. Tek soru. Kullanıcının dilinde yanıt ver. Bilgi uydurma.
            """;

    private String buildSystemPrompt(AICoachRequest request) {
        var sb = new StringBuilder(BASE_PROMPT);
        if (request.moodContext() != null)
            sb.append("\nRUH HALİ: ").append(request.moodContext());
        if (request.recentTopics() != null && !request.recentTopics().isEmpty())
            sb.append("\nKONULAR: ").append(String.join(", ", request.recentTopics()));
        if (request.activeGoals() != null && !request.activeGoals().isEmpty())
            sb.append("\nAKTİF HEDEFLER: ").append(String.join("; ", request.activeGoals()));
        if (request.userSummary() != null)
            sb.append("\nPROFİL: ").append(request.userSummary());
        return sb.toString();
    }

    @Override
    @CircuitBreaker(name = "ai-provider", fallbackMethod = "chatFallback")
    public AICoachResponse chat(AICoachRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (var msg : request.history()) {
            messages.add(Map.of("role", msg.role(), "content", msg.content()));
        }
        messages.add(Map.of("role", "user", "content", request.userMessage()));

        Map<String, Object> requestBody = Map.of(
                "model", props.getAi().getClaude().getCoachModel(),
                "max_tokens", 1000,
                "system", buildSystemPrompt(request),
                "messages", messages
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", props.getAi().getClaude().getApiKey());
        headers.set("anthropic-version", ANTHROPIC_VER);

        ResponseEntity<Map> response = restTemplate.exchange(
                CLAUDE_URL, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        Map<?, ?> body = response.getBody();
        List<?> content = (List<?>) body.get("content");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Claude boş content döndürdü: " + body);
        }
        Map<?, ?> block = (Map<?, ?>) content.get(0);
        return new AICoachResponse((String) block.get("text"));
    }

    private AICoachResponse chatFallback(AICoachRequest request, Throwable ex) {
        log.error("Claude coach devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "AI koç servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
    }
}
