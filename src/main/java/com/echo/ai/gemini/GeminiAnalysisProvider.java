package com.echo.ai.gemini;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAnalysisProvider implements AIAnalysisProvider {

    private final AppProperties props;
    private final ObjectMapper  objectMapper;
    private final RestTemplate  restTemplate;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String PROMPT_TEMPLATE = """
            You are an empathetic journal analyst.
            Analyze the journal transcript and return ONLY valid JSON, no markdown, no explanation:
            {
              "summary": "2-3 sentence natural language summary",
              "mood_score": <float 0.0-1.0>,
              "mood_label": "<very_positive|positive|neutral|negative|very_negative>",
              "topics": ["topic1", "topic2"],
              "reflective_question": "one specific follow-up question",
              "key_emotions": ["emotion1", "emotion2"],
              "energy_level": "<low|medium|high>",
              "goals": [
                {"title": "goal title", "timeframe": "when", "goal_type": "health|work|personal|relationship|general"}
              ],
              "insights": ["behavioral or emotional insight about the user"],
              "achievements": [
                {"title": "what was accomplished", "type": "work|health|personal|relationship"}
              ],
              "calendar_events": [
                {"title": "event name", "scheduled_for": "when (natural language)", "event_type": "health|work|social|personal"}
              ],
              "memory_worthy": false,
              "memory_capsule_title": null
            }
            Only include goals, achievements, calendar_events if explicitly mentioned.
            Set memory_worthy to true only for exceptionally meaningful moments.

            Journal entry: %s
            """;

    @Override
    @CircuitBreaker(name = "ai-provider", fallbackMethod = "analyzeFallback")
    public AIAnalysisResponse analyze(AIAnalysisRequest request) {
        String model  = props.getAi().getGemini().getAnalysisModel();
        String apiKey = props.getAi().getGemini().getApiKey();
        String url    = String.format(GEMINI_URL, model, apiKey);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of(
                                "text", String.format(PROMPT_TEMPLATE, request.transcript())
                        ))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema",   buildAnalysisSchema(),
                        "maxOutputTokens",  2048,
                        "temperature",      0.2   // düşük sıcaklık → tutarlı, deterministik JSON
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        String json = extractGeminiContent(response.getBody());
        return parseResponse(json);
    }

    private AIAnalysisResponse analyzeFallback(AIAnalysisRequest request, Throwable ex) {
        log.error("Gemini analiz devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "AI analiz servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
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

    private AIAnalysisResponse parseResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            List<String> topics = parseStringList(node, "topics");
            List<String> emotions = parseStringList(node, "key_emotions");

            List<AIGoal> goals = parseList(node, "goals", AIGoal.class);
            List<String> insights = parseStringList(node, "insights");
            List<AIDetectedAchievement> achievements = parseList(node, "achievements", AIDetectedAchievement.class);
            List<AICalendarEvent> calendarEvents = parseList(node, "calendar_events", AICalendarEvent.class);

            boolean memoryWorthy = node.path("memory_worthy").asBoolean(false);
            String memoryCapsuleTitle = node.path("memory_capsule_title").isNull()
                    ? null : node.path("memory_capsule_title").asText(null);

            return new AIAnalysisResponse(
                    node.get("summary").asText(),
                    node.get("mood_score").asDouble(),
                    node.get("mood_label").asText(),
                    topics,
                    node.get("reflective_question").asText(),
                    emotions,
                    node.get("energy_level").asText(),
                    goals,
                    insights,
                    achievements,
                    calendarEvents,
                    memoryWorthy,
                    memoryCapsuleTitle,
                    json
            );
        } catch (Exception e) {
            throw new RuntimeException("Gemini yanıtı parse edilemedi: " + json, e);
        }
    }

    private List<String> parseStringList(JsonNode node, String field) {
        JsonNode arrayNode = node.path(field);
        if (arrayNode.isMissingNode() || arrayNode.isNull() || !arrayNode.isArray()) return List.of();
        return objectMapper.convertValue(arrayNode,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private <T> List<T> parseList(JsonNode node, String field, Class<T> type) {
        JsonNode arrayNode = node.path(field);
        if (arrayNode.isMissingNode() || arrayNode.isNull() || !arrayNode.isArray()) return List.of();
        try {
            return objectMapper.convertValue(arrayNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            log.warn("'{}' alanı parse edilemedi, boş liste döndürülüyor: {}", field, e.getMessage());
            return List.of();
        }
    }

    /**
     * Gemini responseSchema — garanti edilmiş JSON yapısı.
     * Gemini bu schema'ya uymayan yanıt DÖNDÜREMEZ (API seviyesinde zorunlu).
     * Gemini type isimleri büyük harf: STRING, NUMBER, BOOLEAN, ARRAY, OBJECT.
     */
    private Map<String, Object> buildAnalysisSchema() {
        // Yardımcı: string property
        Map<String, Object> str  = Map.of("type", "STRING");
        Map<String, Object> num  = Map.of("type", "NUMBER");
        Map<String, Object> bool = Map.of("type", "BOOLEAN");

        // goals[] schema
        Map<String, Object> goalSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "title",     str,
                        "timeframe", str,
                        "goal_type", str
                )
        );

        // achievements[] schema
        Map<String, Object> achievementSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "title", str,
                        "type",  str
                )
        );

        // calendar_events[] schema
        Map<String, Object> calendarSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "title",         str,
                        "scheduled_for", str,
                        "event_type",    str
                )
        );

        // Tam analiz şeması — required alanlar zorunlu, optional alanlar ekleme
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary",             str);
        properties.put("mood_score",          num);
        properties.put("mood_label",          str);
        properties.put("topics",              Map.of("type", "ARRAY", "items", str));
        properties.put("reflective_question", str);
        properties.put("key_emotions",        Map.of("type", "ARRAY", "items", str));
        properties.put("energy_level",        str);
        properties.put("goals",               Map.of("type", "ARRAY", "items", goalSchema));
        properties.put("insights",            Map.of("type", "ARRAY", "items", str));
        properties.put("achievements",        Map.of("type", "ARRAY", "items", achievementSchema));
        properties.put("calendar_events",     Map.of("type", "ARRAY", "items", calendarSchema));
        properties.put("memory_worthy",       bool);
        properties.put("memory_capsule_title", str);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", properties);
        schema.put("required", List.of(
                "summary", "mood_score", "mood_label", "topics",
                "reflective_question", "key_emotions", "energy_level",
                "goals", "insights", "achievements", "calendar_events",
                "memory_worthy"
        ));
        return schema;
    }
}
