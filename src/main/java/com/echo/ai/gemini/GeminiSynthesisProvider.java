package com.echo.ai.gemini;

import com.echo.ai.AISynthesisProvider;
import com.echo.ai.AISynthesisRequest;
import com.echo.ai.AISynthesisResponse;
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

/**
 * Gemini Flash synthesis provider.
 * Synthesizes journal entries, coach exchanges, goals, and memory into
 * a single structured AI response (summary, insights, achievements).
 */
@Slf4j
@Component
public class GeminiSynthesisProvider implements AISynthesisProvider {

    private final AppProperties props;
    private final ObjectMapper  objectMapper;
    private final RestTemplate  restTemplate;
    private final GeminiClient  geminiClient;
    private final String        promptVersion;

    public GeminiSynthesisProvider(AppProperties props,
                                   ObjectMapper objectMapper,
                                   @Qualifier("synthesisRestTemplate") RestTemplate restTemplate,
                                   GeminiClient geminiClient) {
        this.props         = props;
        this.objectMapper  = objectMapper;
        this.restTemplate  = restTemplate;
        this.geminiClient  = geminiClient;
        this.promptVersion = props.getPrompts().getSynthesisVersion();
    }

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String SYNTHESIS_PROMPT_TEMPLATE = """
            You are the AI analysis and memory engine of the Echo app.
            The user's journal entries, coach conversations, goals, stats, and current profile are provided.
            Analyze this data and produce a synthesis.

            Return ONLY valid JSON. No explanation, markdown, code blocks or extra text.
            First character must be {, last character must be }.
            If data is insufficient, return null — do NOT fabricate information.

            JSON FORMAT:
            {
              "narrative_summary": "3-5 sentence user story",
              "suggestions": [{"title": "...", "body": "...", "icon": "SF Symbol name"}],
              "emotional_assessment": "1-2 sentence emotional state",
              "growth_score": 0-100,
              "growth_label": "short growth label",
              "growth_message": "1-2 sentence motivational message",
              "weekly_themes": ["theme1", "theme2"],
              "coach_insight": "1 insight or null",
              "profile_update": {
                "user_profile": "2-3 sentence general profile",
                "emotional_patterns": "recurring emotional patterns",
                "values_strengths": "values and strengths",
                "growth_trajectory": "growth trajectory"
              }
            }

            RULES:
            - Respond entirely in {language}
            - suggestions max 3, specific and actionable
            - growth_score: 0=no progress, 50=consistent, 80+=significant growth
            - narrative_summary: tell a story, NOT statistics
            - coach_insight: null if no coach conversations
            - profile_update: IMPROVE the existing profile, do not erase or reset
            - If data is insufficient, set field to null — do NOT guess
            """;

    @Override
    @CircuitBreaker(name = "gemini-synthesis", fallbackMethod = "synthesizeFallback")
    public AISynthesisResponse synthesize(AISynthesisRequest request) {
        String model  = props.getAi().getGemini().getAnalysisModel();
        String apiKey = props.getAi().getGemini().getApiKey();
        String url    = String.format(GEMINI_URL, model);

        String lang = request.language() != null ? request.language() : "tr";
        String synthesisPrompt = SYNTHESIS_PROMPT_TEMPLATE.replace("{language}", langName(lang));
        String userMessage = buildUserMessage(request);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of(
                                "text", synthesisPrompt + "\n\n[USER DATA]\n" + userMessage
                        ))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "maxOutputTokens",  4096,
                        "temperature",      0.3,
                        // Disable thinking — same token-budget issue as analysis
                        "thinkingConfig",   Map.of("thinkingBudget", 0)
                )
        );

        Map<?, ?> responseBody = geminiClient.execute(
                restTemplate, url, apiKey, requestBody, "SYNTHESIS", promptVersion);

        String json = geminiClient.extractText(responseBody);
        try {
            return parseResponse(json);
        } catch (Exception e) {
            log.error("synthesis_parse_error provider=gemini cause={}", e.getMessage(), e);
            return emptyResponse(json);
        }
    }

    private static String langName(String code) {
        return "en".equals(code) ? "English" : "Turkish";
    }

    private AISynthesisResponse synthesizeFallback(AISynthesisRequest request, Throwable ex) {
        log.error("Gemini synthesis unavailable: {}", ex.getMessage());
        throw new ServiceUnavailableException("AI synthesis service is temporarily unavailable.", ex);
    }

    private AISynthesisResponse emptyResponse(String rawJson) {
        return new AISynthesisResponse(null, List.of(), null, 0, null, null, List.of(), null, null, rawJson);
    }

    private String buildUserMessage(AISynthesisRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("PERİYOT: ").append(req.periodDays()).append(" gün\n");
        sb.append("STREAK: ").append(req.currentStreak()).append(" gün\n");
        sb.append("TOPLAM GİRİŞ: ").append(req.totalEntries()).append("\n");
        sb.append("TAMAMLANAN HEDEF: ").append(req.completedGoalCount()).append("\n");
        if (req.previousPeriodTrend() != null) {
            sb.append("ÖNCEKİ DÖNEM TREND: ").append(req.previousPeriodTrend()).append("\n");
        }

        if (req.userProfile() != null) {
            sb.append("\nMEVCUT PROFİL:\n").append(req.userProfile()).append("\n");
        }

        if (req.activeGoals() != null && !req.activeGoals().isEmpty()) {
            sb.append("\nAKTİF HEDEFLER:\n");
            req.activeGoals().forEach(g -> sb.append("- ").append(g).append("\n"));
        }

        if (req.entries() != null && !req.entries().isEmpty()) {
            sb.append("\nGÜNLÜK GİRİŞLER (").append(req.entries().size()).append(" adet):\n");
            for (AISynthesisRequest.EntrySummary e : req.entries()) {
                sb.append("  [").append(e.date()).append("] ")
                  .append("Ruh hali: ").append(String.format("%.2f", e.moodScore()))
                  .append(" (").append(e.moodLabel()).append(")")
                  .append(", Enerji: ").append(e.energyLevel());
                if (e.topics() != null && !e.topics().isEmpty()) {
                    sb.append(", Konular: ").append(String.join(", ", e.topics()));
                }
                if (e.emotions() != null && !e.emotions().isEmpty()) {
                    sb.append(", Duygular: ").append(String.join(", ", e.emotions()));
                }
                if (e.summary() != null && !e.summary().isBlank()) {
                    sb.append("\n    Özet: ").append(e.summary());
                }
                sb.append("\n");
            }
        }

        if (req.coachExchanges() != null && !req.coachExchanges().isEmpty()) {
            sb.append("\nCOACH KONUŞMALARI (").append(req.coachExchanges().size()).append(" alışveriş):\n");
            for (AISynthesisRequest.CoachExchange ex : req.coachExchanges()) {
                sb.append("  [").append(ex.date()).append("]\n");
                sb.append("  Kullanıcı: ").append(ex.userMessage()).append("\n");
                sb.append("  Coach: ").append(ex.aiResponse()).append("\n");
            }
        }

        return sb.toString();
    }

    /** Strips markdown code blocks and extra text — extracts first { to last } */
    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end   = raw.lastIndexOf('}');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : raw.trim();
    }

    private AISynthesisResponse parseResponse(String raw) {
        String json = extractJson(raw);
        try {
            JsonNode node = objectMapper.readTree(json);

            List<AISynthesisResponse.Suggestion> suggestions = parseSuggestions(node);
            List<String> weeklyThemes = parseStringList(node, "weekly_themes");

            AISynthesisResponse.ProfileUpdate profileUpdate = null;
            JsonNode puNode = node.path("profile_update");
            if (!puNode.isMissingNode() && !puNode.isNull() && puNode.isObject()) {
                profileUpdate = new AISynthesisResponse.ProfileUpdate(
                        nullOrText(puNode, "user_profile"),
                        nullOrText(puNode, "emotional_patterns"),
                        nullOrText(puNode, "values_strengths"),
                        nullOrText(puNode, "growth_trajectory")
                );
            }

            return new AISynthesisResponse(
                    nullOrText(node, "narrative_summary"),
                    suggestions,
                    nullOrText(node, "emotional_assessment"),
                    node.path("growth_score").asInt(0),
                    nullOrText(node, "growth_label"),
                    nullOrText(node, "growth_message"),
                    weeklyThemes,
                    nullOrText(node, "coach_insight"),
                    profileUpdate,
                    json
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini synthesis response", e);
        }
    }

    private List<AISynthesisResponse.Suggestion> parseSuggestions(JsonNode node) {
        JsonNode arr = node.path("suggestions");
        if (arr.isMissingNode() || arr.isNull() || !arr.isArray()) return List.of();
        try {
            return objectMapper.convertValue(arr,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AISynthesisResponse.Suggestion.class));
        } catch (Exception e) {
            log.warn("Could not parse suggestions: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseStringList(JsonNode node, String field) {
        JsonNode arr = node.path(field);
        if (arr.isMissingNode() || arr.isNull() || !arr.isArray()) return List.of();
        return objectMapper.convertValue(arr,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private String nullOrText(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return (f.isMissingNode() || f.isNull()) ? null : f.asText();
    }

}
