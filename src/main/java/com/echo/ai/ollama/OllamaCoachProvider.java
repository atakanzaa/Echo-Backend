package com.echo.ai.ollama;

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

/**
 * Ollama — OpenAI-uyumlu API formatıyla yerel/self-hosted LLM desteği.
 * Varsayılan: http://localhost:11434/v1/chat/completions
 * OLLAMA_BASE_URL env değişkeniyle başka sunucuya yönlendirilebilir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaCoachProvider implements AICoachProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;

    private static final String BASE_PROMPT = """
            Sen Echo, kişisel gelişim odaklı empatik bir AI yansıma koçusun.
            Uzmanlık: zihinsel sağlık, yaşam koçluğu, kariyer.
            ÇERÇEVE: Kabul Et → Yansıt → Keşfet → Bağla → Güçlendir.
            SINIRLAR: Tıbbi, hukuki, finansal, konu dışı sorulara girme.
            GÜVENLİK: Kriz ifadelerinde 182 (Ruh Sağlığı Hattı) paylaş.
            KURALLAR: 2-4 cümle. Tek soru. Kullanıcının dilinde yanıt ver.
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
        String url = props.getAi().getOllama().getBaseUrl() + "/v1/chat/completions";

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(request)));
        for (var msg : request.history()) {
            messages.add(Map.of("role", msg.role(), "content", msg.content()));
        }
        messages.add(Map.of("role", "user", "content", request.userMessage()));

        Map<String, Object> body = Map.of(
                "model", props.getAi().getOllama().getCoachModel(),
                "messages", messages,
                "max_tokens", 1000,
                "stream", false
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class
        );

        List<?> choices = (List<?>) response.getBody().get("choices");
        Map<?, ?> choice  = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return new AICoachResponse((String) message.get("content"));
    }

    private AICoachResponse chatFallback(AICoachRequest request, Throwable ex) {
        log.error("Ollama coach devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "AI koç servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
    }
}
