package com.echo.ai.claude;

import com.echo.ai.*;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class ClaudeAnalysisProvider implements AIAnalysisProvider {

    private final AppProperties props;
    private final ObjectMapper  objectMapper;
    private final RestTemplate  restTemplate;

    public ClaudeAnalysisProvider(AppProperties props,
                                  ObjectMapper objectMapper,
                                  @Qualifier("analysisRestTemplate") RestTemplate restTemplate) {
        this.props        = props;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    private static final String CLAUDE_URL    = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VER = "2023-06-01";

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
                {
                  "title": "short actionable goal title",
                  "timeframe": "when",
                  "goal_type": "health|work|personal|relationship|general",
                  "confidence": <float 0.0-1.0>,
                  "reason": "why this is a meaningful goal suggestion",
                  "source_quote": "brief quote from the user"
                }
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
            Goals are suggestions, not tasks to auto-create. Include at most 2 goals and only when they are user-owned, short-term, actionable, and clearly stated.
            Do not turn vague wishes, abstract self-improvement ideas, or weak possibilities into goals.
            Only include goals, achievements, calendar_events if explicitly mentioned.
            Set memory_worthy to true only for exceptionally meaningful moments.
            """;

    private static final String GOAL_MATCH_SYSTEM_PROMPT = """
            You verify whether a user utterance clearly completes one of the user's open goals.
            Return ONLY valid JSON, no markdown, no explanation:
            {
              "goal_id": "uuid or null",
              "confidence": <float 0.0-1.0>,
              "should_auto_complete": false,
              "needs_confirmation": false,
              "reason": "short explanation"
            }
            Rules:
            - Select at most one goal.
            - Future intent, uncertainty, or negation is NOT completion.
            - If unsure, keep should_auto_complete false.
            - Use needs_confirmation only when one goal is plausible but not certain.
            """;

    @Override
    @CircuitBreaker(name = "claude-analysis", fallbackMethod = "analyzeFallback")
    public AIAnalysisResponse analyze(AIAnalysisRequest request) {
        Map<String, Object> requestBody = Map.of(
                "model", props.getAi().getClaude().getAnalysisModel(),
                "max_tokens", 2048,
                "system", SYSTEM_PROMPT,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", "Respond in " + langName(request.language()) + ". " +
                                   "Analyze this journal entry: " + sanitizeUserInput(request.transcript())
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", props.getAi().getClaude().getApiKey());
        headers.set("anthropic-version", ANTHROPIC_VER);

        ResponseEntity<Map> response = restTemplate.exchange(
                CLAUDE_URL, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        String json = extractClaudeContent(response.getBody());
        return parseResponse(json);
    }

    @Override
    @CircuitBreaker(name = "claude-analysis", fallbackMethod = "verifyGoalMatchFallback")
    public GoalMatchDecision verifyGoalMatch(GoalMatchVerificationRequest request) {
        Map<String, Object> requestBody = Map.of(
                "model", props.getAi().getClaude().getAnalysisModel(),
                "max_tokens", 512,
                "system", GOAL_MATCH_SYSTEM_PROMPT,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", buildGoalMatchPrompt(request)
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", props.getAi().getClaude().getApiKey());
        headers.set("anthropic-version", ANTHROPIC_VER);

        ResponseEntity<Map> response = restTemplate.exchange(
                CLAUDE_URL, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        return parseGoalMatchDecision(extractClaudeContent(response.getBody()));
    }

    private static String langName(String code) {
        return "en".equals(code) ? "English" : "Turkish";
    }

    private AIAnalysisResponse analyzeFallback(AIAnalysisRequest request, Throwable ex) {
        log.error("Claude analysis circuit open: {}", ex.getMessage());
        throw new ServiceUnavailableException("AI analysis service is temporarily unavailable.", ex);
    }

    /** Strips known prompt injection vectors from user-supplied content. */
    private String sanitizeUserInput(String input) {
        if (input == null) return "";
        return input
                .replaceAll("(?i)</?SYSTEM>", "")
                .replaceAll("(?i)ignore (all )?previous instructions?", "[filtered]")
                .replaceAll("(?i)you are now", "[filtered]")
                .replaceAll("(?i)\\bact as\\b(?! (a user|the user))", "[filtered]")
                .replaceAll("(?i)\\bnew instructions?\\b", "[filtered]");
    }

    private GoalMatchDecision verifyGoalMatchFallback(GoalMatchVerificationRequest request, Throwable ex) {
        log.error("Claude goal match verifier unavailable: {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "AI goal matching service is temporarily unavailable.", ex);
    }

    @SuppressWarnings("unchecked")
    private String extractClaudeContent(Map<?, ?> body) {
        List<?> content = (List<?>) body.get("content");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Claude returned empty content: " + body);
        }
        Map<?, ?> block = (Map<?, ?>) content.get(0);
        return (String) block.get("text");
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
            throw new RuntimeException("Claude yanıtı parse edilemedi: " + json, e);
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

    private GoalMatchDecision parseGoalMatchDecision(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            UUID goalId = null;
            JsonNode goalIdNode = node.path("goal_id");
            if (!goalIdNode.isMissingNode() && !goalIdNode.isNull()) {
                String rawGoalId = goalIdNode.asText();
                if (rawGoalId != null && !rawGoalId.isBlank()) {
                    goalId = UUID.fromString(rawGoalId);
                }
            }

            return new GoalMatchDecision(
                    goalId,
                    node.path("confidence").asDouble(0.0),
                    node.path("should_auto_complete").asBoolean(false),
                    node.path("needs_confirmation").asBoolean(false),
                    node.path("reason").asText("")
            );
        } catch (Exception e) {
            throw new RuntimeException("Claude goal match response could not be parsed: " + json, e);
        }
    }

    private String buildGoalMatchPrompt(GoalMatchVerificationRequest request) {
        try {
            return "Respond in " + langName(request.language()) + ".\n" +
                    "User utterance:\n" + request.utterance() + "\n\n" +
                    "Open goal candidates:\n" + objectMapper.writeValueAsString(request.candidates());
        } catch (Exception e) {
            throw new RuntimeException("Goal match request could not be serialized", e);
        }
    }
}
