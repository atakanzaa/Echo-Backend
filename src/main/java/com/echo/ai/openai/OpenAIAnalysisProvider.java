package com.echo.ai.openai;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIAnalysisProvider implements AIAnalysisProvider {

    private final AppProperties props;
    private final ObjectMapper  objectMapper;
    private final RestTemplate  restTemplate;

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
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
            """;

    @Override
    @CircuitBreaker(name = "ai-provider", fallbackMethod = "analyzeFallback")
    public AIAnalysisResponse analyze(AIAnalysisRequest request) {
        Map<String, Object> requestBody = Map.of(
                "model", props.getAi().getOpenai().getAnalysisModel(),
                "max_tokens", 2048,
                "temperature", 0.3,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content",
                               "Analyze this journal entry: " + request.transcript())
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getAi().getOpenai().getApiKey());

        ResponseEntity<Map> response = restTemplate.exchange(
                CHAT_URL, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        String json = extractContent(response.getBody());
        return parseResponse(json);
    }

    private String extractContent(Map<?, ?> body) {
        List<?> choices = (List<?>) body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("OpenAI boş choices döndürdü: " + body);
        }
        Map<?, ?> choice  = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return (String) message.get("content");
    }

    private AIAnalysisResponse analyzeFallback(AIAnalysisRequest request, Throwable ex) {
        log.error("OpenAI analiz devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "AI analiz servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
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
            throw new RuntimeException("OpenAI yanıtı parse edilemedi: " + json, e);
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
}
