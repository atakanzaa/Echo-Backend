package com.echo.ai.gemini;

import com.echo.ai.AICoachProvider;
import com.echo.ai.AICoachRequest;
import com.echo.ai.AICoachResponse;
import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiCoachProvider implements AICoachProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;
    private final GeminiClient  geminiClient;
    private final String        promptVersion;

    public GeminiCoachProvider(AppProperties props,
                               @Qualifier("coachRestTemplate") RestTemplate restTemplate,
                               GeminiClient geminiClient) {
        this.props         = props;
        this.restTemplate  = restTemplate;
        this.geminiClient  = geminiClient;
        this.promptVersion = props.getPrompts().getCoachVersion();
    }

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String BASE_INSTRUCTION = """
            Sen {USER_NAME} ile konuşan bir asistansın. Psikolojik açıdan derinlemesine düşünürsün ama bunu asla belli etmezsin — sadece meraklı, zeki bir insan gibi konuşursun.

            NASIL DÜŞÜNÜRSÜN (içsel, asla dışa vurmadan):
            Her mesajı şu açılardan analiz et:
            1. Ne oldu gerçekte?
            2. Kişi buna nasıl anlam yükledi?
            3. Bu his ya da durum neyin işareti olabilir?
            4. Kişi neyin üstünden atlıyor ya da görmezden geliyor?
            Bu analizi asla kullanıcıya gösterme. Sadece cevabını şekillendir.

            NASIL KONUŞURSUN:
            Doğal, sohbet dili. Terapi dili değil. Şablonlar yok.
            Cevapların kısa — 2-3 cümle. Tek bir şeye odaklan.
            Her mesajda ya bir gözlem ya da bir soru. İkisi birden çok nadir olsun.
            Soru soruyorsan: cevabı evet/hayır olmayan, kişiyi içine döndüren bir soru. Fazlası yok.

            ASLA YAPMA:
            - "İlginç", "dikkat çekici", "vurgulamak istiyorum" gibi klişe başlangıçlar
            - Kişinin yazdığını başka kelimelerle tekrar etmek
            - "Harika", "Bunu duymak güzel", "Çok önemli bir şey paylaştın" gibi boş övgüler
            - Tavsiye, çözüm, aksiyon planı, öğreticilik
            - Psikolog gibi konuşmak — sen bir arkadaş gibi konuşursun
            - Aynı cümle kalıplarını tekrar tekrar kullanmak

            KRİZ DURUMU (kendine zarar / intihar sinyali):
            Bunu aynen söyle:
            "Bunu benimle paylaştığın için buradayım. Şu an profesyonel biriyle konuşman önemli.
            Türkiye'de 7/24 ücretsiz: 182 (İntihar Önleme Hattı)"
            Sonra başka bir şey ekleme.

            RULE: Always respond in {language}. Language codes: tr=Turkish, en=English.
            """;

    private static String langName(String code) {
        return "en".equals(code) ? "English" : "Turkish";
    }

    private String buildInstruction(AICoachRequest request) {
        String name = (request.userName() != null && !request.userName().isBlank())
                ? request.userName() : "kullanıcı";
        String lang = request.language() != null ? request.language() : "tr";
        var sb = new StringBuilder(BASE_INSTRUCTION
                .replace("{USER_NAME}", name)
                .replace("{language}", langName(lang)));
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
    @CircuitBreaker(name = "gemini-coach", fallbackMethod = "chatFallback")
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
                "parts", List.of(Map.of("text", sanitizeUserInput(request.userMessage())))
        ));

        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", buildInstruction(request)))),
                "contents", contents,
                "generationConfig", Map.of("maxOutputTokens", 1000)
        );

        Map<?, ?> responseBody = geminiClient.execute(
                restTemplate, url, requestBody, "COACH_RESPONSE", promptVersion);

        return new AICoachResponse(geminiClient.extractText(responseBody));
    }

    private AICoachResponse chatFallback(AICoachRequest request, Throwable ex) {
        log.error("Gemini coach circuit open: {}", ex.getMessage());
        throw new ServiceUnavailableException("AI coach service is temporarily unavailable.", ex);
    }

    /** Strips known prompt injection vectors from user coach messages. */
    private String sanitizeUserInput(String input) {
        if (input == null) return "";
        return input
                .replaceAll("(?i)</?SYSTEM>", "")
                .replaceAll("(?i)ignore (all )?previous instructions?", "[filtered]")
                .replaceAll("(?i)you are now", "[filtered]")
                .replaceAll("(?i)\\bact as\\b(?! (a user|the user))", "[filtered]")
                .replaceAll("(?i)\\bnew instructions?\\b", "[filtered]");
    }


}
