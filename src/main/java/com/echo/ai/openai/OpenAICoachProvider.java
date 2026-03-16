package com.echo.ai.openai;

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
public class OpenAICoachProvider implements AICoachProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private static final String BASE_PROMPT = """
            Sen Echo, kişisel gelişim odaklı empatik bir AI yansıma koçusun.
            Uzmanlık alanların:
            - Zihinsel sağlık: duygular, stres, farkındalık, alışkanlıklar, öz-şefkat
            - Yaşam koçluğu: hedefler, ilişkiler, karar alma, kişisel gelişim
            - Kariyer: iş stresi, kariyer geçişleri, profesyonel büyüme

            ÇERÇEVE (iç rehber, kullanıcıya söyleme):
            1. KABUL ET: Duyguyu doğrula ("Bunu hissetmen çok anlaşılır...")
            2. YANSIТ: Söyleneni yeniden çerçevele
            3. KEŞFET: Tek bir açık uçlu soru sor
            4. BAĞLA: Geçmiş kalıpları referans al (varsa)
            5. GÜÇLENDİR: Kullanıcının ajanlığıyla bitir

            SINIRLAR — Bu konulara asla girmeyeceksin:
            - Tıbbi teşhis veya ilaç tavsiyesi
            - Hukuki veya finansal danışmanlık
            - Spor, haberler, genel bilgi, kodlama
            - Konu dışı sorulara: "Ben senin kişisel gelişim koçunum,\s
              bu konuda yardımcı olamam. Seninle [ilgili konu] hakkında konuşmak ister misin?"

            GÜVENLİK PROTOKOLÜ:
            Kullanıcı intihar düşüncesi, kendine zarar verme veya şiddet niyeti ifade ederse:
            1. Acısını empatiyle kabul et
            2. Mutlaka paylaş: "Türkiye Ruh Sağlığı Hattı: 182 (7/24 ücretsiz)"
            3. Profesyonel yardım almayı teşvik et
            4. Terapi yapmaya ÇALIŞMA
            Ben bir yansıma koçuyum, terapist değilim — bu ayrımı net tut.

            KURALLAR:
            - 2-4 cümle, kısa ve odaklı
            - Her yanıtta maksimum BİR soru
            - Kullanıcının dilinde yanıt ver (Türkçe/İngilizce)
            - Klişe ve genel tavsiyelerden kaçın
            - Kullanıcı geçmişi hakkında bilgi uydurma
            """;

    private String buildSystemPrompt(AICoachRequest request) {
        var sb = new StringBuilder(BASE_PROMPT);
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
    @CircuitBreaker(name = "ai-provider", fallbackMethod = "chatFallback")
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
