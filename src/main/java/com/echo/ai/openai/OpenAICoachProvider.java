package com.echo.ai.openai;

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
public class OpenAICoachProvider implements AICoachProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;

    public OpenAICoachProvider(AppProperties props,
                               @Qualifier("coachRestTemplate") RestTemplate restTemplate) {
        this.props        = props;
        this.restTemplate = restTemplate;
    }

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private static final String BASE_PROMPT = """
            Sen kullanıcıyla konuşan bir asistansın. Psikolojik açıdan derinlemesine düşünürsün ama bunu asla belli etmezsin — sadece meraklı, zeki bir insan gibi konuşursun.

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

    private String buildSystemPrompt(AICoachRequest request) {
        String lang = request.language() != null ? request.language() : "tr";
        var sb = new StringBuilder(BASE_PROMPT.replace("{language}", langName(lang)));
        if (request.moodContext() != null) {
            sb.append("\nKULLANICI RUH HALİ BAĞLAMI:\n").append(request.moodContext());
        }
        if (request.recentTopics() != null && !request.recentTopics().isEmpty()) {
            sb.append("\nSON KONULAR: ").append(String.join(", ", request.recentTopics()));
        }
        if (request.activeGoals() != null && !request.activeGoals().isEmpty()) {
            sb.append("\nAKTİF HEDEFLER: ").append(String.join("; ", request.activeGoals()));
        }
        if (request.userSummary() != null) {
            sb.append("\nKULLANICI PROFİLİ:\n").append(request.userSummary());
        }
        return sb.toString();
    }

    @Override
    @CircuitBreaker(name = "openai-coach", fallbackMethod = "chatFallback")
    public AICoachResponse chat(AICoachRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(request)));

        for (var msg : request.history()) {
            messages.add(Map.of("role", msg.role(), "content", msg.content()));
        }
        messages.add(Map.of("role", "user", "content", request.userMessage()));

        Map<String, Object> requestBody = Map.of(
                "model", props.getAi().getOpenai().getCoachModel(),
                "messages", messages,
                "max_tokens", 1000,
                "temperature", 0.3
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getAi().getOpenai().getApiKey());

        ResponseEntity<Map> response = restTemplate.exchange(
                CHAT_URL, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        Map<?, ?> body = response.getBody();
        List<?> choices = (List<?>) body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("OpenAI boş choices döndürdü: " + body);
        }
        Map<?, ?> choice  = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return new AICoachResponse((String) message.get("content"));
    }

    private AICoachResponse chatFallback(AICoachRequest request, Throwable ex) {
        log.error("OpenAI coach devre dışı (circuit open): {}", ex.getMessage());
        throw new ServiceUnavailableException(
                "AI koç servisi şu anda kullanılamıyor, lütfen birkaç dakika sonra tekrar deneyin.", ex);
    }
}
