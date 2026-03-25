package com.echo.ai.openai;

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
 * OpenAI tabanlı synthesis provider.
 * response_format: json_object ile yapılandırılmış JSON garantisi.
 * max_tokens=1000, temperature=0.3 — tutarlı ve ekonomik çıktı.
 */
@Slf4j
@Component
public class OpenAISynthesisProvider implements AISynthesisProvider {

    private final AppProperties props;
    private final ObjectMapper  objectMapper;
    private final RestTemplate  restTemplate;

    public OpenAISynthesisProvider(AppProperties props,
                                   ObjectMapper objectMapper,
                                   @Qualifier("synthesisRestTemplate") RestTemplate restTemplate) {
        this.props        = props;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            Sen Echo uygulamasının AI analiz ve hafıza motorusun.
            Kullanıcının günlük girişleri, coach konuşmaları, hedefleri, istatistikleri
            ve mevcut profili sana veriliyor. Bu verileri analiz ederek sentez üret.

            SADECE geçerli JSON döndür. Açıklama, markdown veya ek metin ekleme.
            Bilgi yetersizse null döndür, bilgi UYDURMA.

            JSON Formatı:
            {
              "narrative_summary": "3-5 cümle, kullanıcının bu döneme ait hikayesi",
              "suggestions": [{"title": "...", "body": "...", "icon": "SF Symbol adı"}],
              "emotional_assessment": "1-2 cümle duygusal durum",
              "growth_score": 0-100,
              "growth_label": "Türkçe gelişim etiketi",
              "growth_message": "1-2 cümle motivasyon",
              "weekly_themes": ["tema1", "tema2"],
              "coach_insight": "coach konuşmasından 1 insight veya null",
              "profile_update": {
                "user_profile": "kullanıcının genel profili (2-3 cümle)",
                "emotional_patterns": "tekrarlayan duygusal kalıplar",
                "values_strengths": "değerler ve güçlü yönler",
                "growth_trajectory": "gelişim yörüngesi (1 cümle)"
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
    @CircuitBreaker(name = "openai-synthesis", fallbackMethod = "synthesizeFallback")
    public AISynthesisResponse synthesize(AISynthesisRequest request) {
        Map<String, Object> requestBody = Map.of(
                "model",           props.getAi().getOpenai().getAnalysisModel(),
                "max_tokens",      1000,
                "temperature",     0.3,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user",   "content", buildUserMessage(request))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getAi().getOpenai().getApiKey());

        ResponseEntity<Map> response = restTemplate.exchange(
                CHAT_URL, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        String raw = extractContent(response.getBody());
        log.debug("OpenAI synthesis yanıtı alındı, parse ediliyor");
        return parseResponse(raw);
    }

    private AISynthesisResponse synthesizeFallback(AISynthesisRequest request, Throwable ex) {
        log.error("OpenAI synthesis devre dışı (circuit open): {}", ex.getMessage());
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

    private String extractContent(Map<?, ?> body) {
        List<?> choices = (List<?>) body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("OpenAI boş choices döndürdü: " + body);
        }
        Map<?, ?> choice  = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return (String) message.get("content");
    }

    /** Markdown kod bloğu veya fazladan metin varsa temizler — ilk { ile son } arasını alır. */
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
            throw new RuntimeException("OpenAI synthesis yanıtı parse edilemedi: " + raw, e);
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
