package com.echo.ai.ollama;

import com.echo.ai.*;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Ollama analiz provider — self-hosted LLM ile günlük analizi.
 * OpenAI-uyumlu endpoint kullanır.
 * Not: Küçük modeller (llama3.1:8b) yeni alanları atlayabilir.
 * Tüm yeni alanlar lenient parse ile List.of() varsayılan döndürür.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaAnalysisProvider implements AIAnalysisProvider {

    private final AppProperties props;
    private final ObjectMapper  objectMapper;
    private final RestTemplate  restTemplate;

    private static final String SYSTEM_PROMPT = """
            Sen empatik bir günlük analistsin. Kullanıcının Türkçe veya İngilizce günlük girişini
            analiz edeceksin. Aşağıdaki JSON formatında yanıt ver (sadece JSON, başka metin yok):
            {
              "summary": "kısa özet (2-3 cümle)",
              "mood_score": 0.0-1.0 arası ondalık sayı,
              "mood_label": "very_negative|negative|neutral|positive|very_positive",
              "topics": ["konu1", "konu2"],
              "reflective_question": "düşündürücü soru",
              "key_emotions": ["duygu1", "duygu2"],
              "energy_level": "low|medium|high",
              "goals": [
                {"title": "hedef başlığı", "timeframe": "ne zaman", "goal_type": "health|work|personal|general"}
              ],
              "insights": ["kullanıcı hakkında davranışsal veya duygusal içgörü"],
              "achievements": [
                {"title": "başarılan şey", "type": "work|health|personal"}
              ],
              "calendar_events": [
                {"title": "etkinlik adı", "scheduled_for": "ne zaman", "event_type": "health|work|social|personal"}
              ],
              "memory_worthy": false,
              "memory_capsule_title": null
            }
            Sadece açıkça bahsedilen hedefleri, başarıları ve etkinlikleri ekle.
            """;

    @Override
    @CircuitBreaker(name = "ai-provider", fallbackMethod = "analyzeFallback")
    public AIAnalysisResponse analyze(AIAnalysisRequest request) {
        String url = props.getAi().getOllama().getBaseUrl() + "/v1/chat/completions";

        Map<String, Object> body = Map.of(
                "model", props.getAi().getOllama().getAnalysisModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", request.transcript())
                ),
                "max_tokens", 2048,
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
        String raw = (String) message.get("content");

        return parseAnalysisJson(raw);
    }

    private AIAnalysisResponse analyzeFallback(AIAnalysisRequest request, Throwable ex) {
        log.error("Ollama analiz devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "AI analiz servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
    }

    private AIAnalysisResponse parseAnalysisJson(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);

            // Temel alanlar — lenient path() ile varsayılan değer
            List<String> topics   = safeStringList(node, "topics");
            List<String> emotions = safeStringList(node, "key_emotions");

            // Yeni alanlar — küçük modeller atlayabilir, List.of() varsayılan
            List<AIGoal>                goals         = safeList(node, "goals",          AIGoal.class);
            List<String>                insights      = safeStringList(node, "insights");
            List<AIDetectedAchievement> achievements  = safeList(node, "achievements",   AIDetectedAchievement.class);
            List<AICalendarEvent>       calendarEvents= safeList(node, "calendar_events",AICalendarEvent.class);
            boolean  memoryWorthy       = node.path("memory_worthy").asBoolean(false);
            String   memoryCapsuleTitle = node.path("memory_capsule_title").isNull()
                    ? null : node.path("memory_capsule_title").asText(null);

            return new AIAnalysisResponse(
                    node.path("summary").asText(""),
                    node.path("mood_score").asDouble(0.5),
                    node.path("mood_label").asText("neutral"),
                    topics,
                    node.path("reflective_question").asText(""),
                    emotions,
                    node.path("energy_level").asText("medium"),
                    goals,
                    insights,
                    achievements,
                    calendarEvents,
                    memoryWorthy,
                    memoryCapsuleTitle,
                    raw
            );
        } catch (Exception e) {
            log.error("Ollama analiz JSON parse hatası: {}", e.getMessage());
            // Tam parse hatasında güvenli varsayılan döndür
            return new AIAnalysisResponse(
                    "", 0.5, "neutral",
                    List.of(), "", List.of(), "medium",
                    List.of(), List.of(), List.of(), List.of(),
                    false, null, raw
            );
        }
    }

    private List<String> safeStringList(JsonNode node, String field) {
        JsonNode arrayNode = node.path(field);
        if (arrayNode.isMissingNode() || arrayNode.isNull() || !arrayNode.isArray()) return List.of();
        try {
            return objectMapper.convertValue(arrayNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private <T> List<T> safeList(JsonNode node, String field, Class<T> type) {
        JsonNode arrayNode = node.path(field);
        if (arrayNode.isMissingNode() || arrayNode.isNull() || !arrayNode.isArray()) return List.of();
        try {
            return objectMapper.convertValue(arrayNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            log.warn("Ollama '{}' alanı parse edilemedi: {}", field, e.getMessage());
            return List.of();
        }
    }
}
