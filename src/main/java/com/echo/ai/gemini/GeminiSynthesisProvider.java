package com.echo.ai.gemini;

import com.echo.ai.AISynthesisProvider;
import com.echo.ai.AISynthesisRequest;
import com.echo.ai.AISynthesisResponse;
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
 * Gemini Flash tabanlı synthesis provider.
 * Kullanıcının tüm veri kaynaklarını (journal, coach, goals, memory) analiz ederek
 * Summary / Insights / Achievements için tek bir AI çağrısında sentez üretir.
 * maxOutputTokens=500, temperature=0.3, responseSchema ile yapılandırılmış JSON garantisi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiSynthesisProvider implements AISynthesisProvider {

    private final AppProperties props;
    private final ObjectMapper  objectMapper;
    private final RestTemplate  restTemplate;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String SYNTHESIS_PROMPT = """
            Sen Echo uygulamasının AI analiz ve hafıza motorusun.
            Kullanıcının günlük girişleri, coach konuşmaları, hedefleri, istatistikleri
            ve mevcut profili sana veriliyor. Bu verileri analiz ederek sentez üret.

            SADECE geçerli JSON döndür. Açıklama, markdown, kod bloğu veya ek metin EKLEME.
            İlk karakter { olmalı, son karakter } olmalı.
            Bilgi yetersizse null döndür, bilgi UYDURMA.

            JSON FORMATI:
            {
              "narrative_summary": "3-5 cümle kullanıcı hikayesi",
              "suggestions": [{"title": "...", "body": "...", "icon": "SF Symbol adı"}],
              "emotional_assessment": "1-2 cümle duygusal durum",
              "growth_score": 0-100,
              "growth_label": "Türkçe etiket",
              "growth_message": "1-2 cümle motivasyon",
              "weekly_themes": ["tema1", "tema2"],
              "coach_insight": "1 insight veya null",
              "profile_update": {
                "user_profile": "2-3 cümle genel profil",
                "emotional_patterns": "tekrarlayan duygusal kalıplar",
                "values_strengths": "değerler ve güçlü yönler",
                "growth_trajectory": "gelişim yörüngesi"
              }
            }

            KURALLAR:
            - Türkçe yanıt ver
            - suggestions max 3, spesifik ve uygulanabilir olsun
            - growth_score: 0=ilerleme yok, 50=düzenli, 80+=önemli gelişim
            - narrative_summary: istatistik DEĞİL, hikaye anlat
            - coach_insight: coach konuşması yoksa null bırak
            - profile_update: mevcut profili GELİŞTİR, silme veya sıfırlama yapma
            - Bilgi yetersizse ilgili alanı null yap, TAHMIN ETME
            """;

    @Override
    @CircuitBreaker(name = "ai-provider", fallbackMethod = "synthesizeFallback")
    public AISynthesisResponse synthesize(AISynthesisRequest request) {
        String model  = props.getAi().getGemini().getAnalysisModel();
        String apiKey = props.getAi().getGemini().getApiKey();
        String url    = String.format(GEMINI_URL, model, apiKey);

        String userMessage = buildUserMessage(request);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of(
                                "text", SYNTHESIS_PROMPT + "\n\n[KULLANICI VERİLERİ]\n" + userMessage
                        ))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",  // saf JSON garantisi — prefix/markdown yok
                        "maxOutputTokens",  4000,                // Türkçe synthesis JSON 2000'i de aşıyor
                        "temperature",      0.3
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        String json = extractGeminiContent(response.getBody());
        log.debug("Gemini synthesis yanıtı alındı, parse ediliyor");
        return parseResponse(json);
    }

    private AISynthesisResponse synthesizeFallback(AISynthesisRequest request, Throwable ex) {
        log.error("Gemini synthesis devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "AI sentez servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
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

    /** Markdown kod bloğu, backtick veya fazladan metin varsa temizler — ilk { ile son } arasını alır. */
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
            throw new RuntimeException("Gemini synthesis yanıtı parse edilemedi: " + json, e);
        }
    }

    private List<AISynthesisResponse.Suggestion> parseSuggestions(JsonNode node) {
        JsonNode arr = node.path("suggestions");
        if (arr.isMissingNode() || arr.isNull() || !arr.isArray()) return List.of();
        try {
            return objectMapper.convertValue(arr,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AISynthesisResponse.Suggestion.class));
        } catch (Exception e) {
            log.warn("Suggestions parse edilemedi: {}", e.getMessage());
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
