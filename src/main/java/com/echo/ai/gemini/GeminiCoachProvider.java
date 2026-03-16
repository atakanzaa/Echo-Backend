package com.echo.ai.gemini;

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
public class GeminiCoachProvider implements AICoachProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String BASE_INSTRUCTION = """
            Sen Echo, kişisel gelişim odaklı empatik bir AI yansıma koçusun.
            Uzmanlık: zihinsel sağlık, yaşam koçluğu, kariyer.
            ÇERÇEVE: Kabul Et → Yansıt → Keşfet → Bağla → Güçlendir.
            SINIRLAR: Tıbbi, hukuki, finansal konu yok. Konu dışı sorulara nazikçe yönlendir.
            GÜVENLİK: Kriz ifadelerinde 182 (Ruh Sağlığı Hattı) paylaş.
            KURALLAR: 2-4 cümle. Tek soru. Kullanıcının dilinde yanıt ver.
            """;

    private String buildInstruction(AICoachRequest request) {
        var sb = new StringBuilder(BASE_INSTRUCTION);
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
        String model  = props.getAi().getGemini().getCoachModel();
        String apiKey = props.getAi().getGemini().getApiKey();
        String url    = String.format(GEMINI_URL, model, apiKey);

        List<Map<String, Object>> contents = new ArrayList<>();
        for (var msg : request.history()) {
            String geminiRole = "user".equals(msg.role()) ? "user" : "model";
            contents.add(Map.of(
                    "role", geminiRole,
                    "parts", List.of(Map.of("text", msg.content()))
            ));
        }
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", request.userMessage()))
        ));

        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", buildInstruction(request)))),
                "contents", contents,
                "generationConfig", Map.of("maxOutputTokens", 1000)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        return new AICoachResponse(extractGeminiContent(response.getBody()));
    }

    private AICoachResponse chatFallback(AICoachRequest request, Throwable ex) {
        log.error("Gemini coach devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "AI koç servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
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
