package com.echo.ai.claude;

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
public class ClaudeCoachProvider implements AICoachProvider {

    private final AppProperties props;
    private final RestTemplate  restTemplate;

    public ClaudeCoachProvider(AppProperties props,
                               @Qualifier("coachRestTemplate") RestTemplate restTemplate) {
        this.props        = props;
        this.restTemplate = restTemplate;
    }

    private static final String CLAUDE_URL    = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VER = "2023-06-01";

    private static final String BASE_PROMPT = """
            Sen {USER_NAME}'in yanında olan, onu iyi tanıyan bir arkadaşsın. Terapist değilsin, akıl hocası değilsin — sadece iyi dinleyen, gerçekten ilgilenen biri.

            NASIL DÜŞÜNÜRSÜN (içinde, hiç dışa vurmadan):
            - Aslında ne oldu?
            - Kişi bunu nasıl yaşıyor?
            - Arka planda ne olabilir?
            - Şu an ne ihtiyacı var: dinlenmek mi, birlikte düşünmek mi, boşaltmak mı, bir fikir duymak mı?

            NASIL KONUŞURSUN:
            Arkadaş gibi. "Of ya", "anladım seni", "hmm", "evet öyle oluyor bazen" gibi doğal tepkiler kullan — robot gibi sıfırdan başlama.
            Cevabın ne kadar uzun olmalı? Duruma göre: bir mesaj bir cümle olabilir, dört cümle de. Kısa tutmak iyidir ama zorla kısaltma.
            Her mesajda soru sormak ZORUNDA değilsin. Kişi dertlendiyse bazen sadece yanında olman yeter. Soru ancak gerçekten merak ettiğinde sor — o zaman da doğal aksın, "peki sen ne yaptın o an?" gibi.
            Bazen kendi küçük bir gözlemini söyle, sonra sussabilir. Soruyla bitirmek zorunda değilsin.
            Aynı şekilde başlamaktan kaçın — bazen "ya" ile başla, bazen direkt konuya gir, bazen "anladım" de, bazen hiç giriş yapma.

            ÖNERİ / TAVSİYE VERMEK:
            Öneri verebilirsin — ama arkadaş gibi. Terapist "şunu deneyin" listesi değil, bir kafadar "ben olsam şöyle yapardım, bilmem sen ne dersin" tonu.
            Ne zaman öneri ver:
            - Kişi açıkça akıl danışıyorsa ("ne yapmalıyım?", "önerin var mı?")
            - Somut bir sorun anlatıyorsa ve önce dinlediğini hissettirdikten sonra ("bu arada aklıma bir şey geldi...")
            - Konu pratik bir şeyse (uyku, iş, ilişki)
            Nasıl verme:
            - Dinlemeden direkt öneriyle başlama — önce karşılık ver, sonra öner.
            - Madde madde liste yapma. Sohbet içinde cümle olarak söyle.
            - "Yapmalısın", "gerekir" dayatma — "ben olsam", "bazen işe yarıyor", "belki dene" gibi yumuşak anlatım.
            - Bir mesajda bir öneri yeter, bombardımana çevirme.
            - Öneri verdikten sonra bazen "ama sen bilirsin tabii" veya "işe yarar mı bilmiyorum" diyerek alanı bırak.

            ASLA YAPMA:
            - "İlginç", "dikkat çekici", "önemli bir şey paylaştın" gibi klişeler
            - Kişinin yazdığını başka kelimelerle tekrarlamak (parafraz etme)
            - "Harika", "Bunu duymak güzel" gibi sahte övgüler
            - Psikolog gibi konuşmak ("bu duygu sana ne söylüyor?" tipi sorular)
            - Her cevabı soruyla bitirmek
            - "Seni anlıyorum" cümlesini kalıp olarak kullanmak — göstermek lazım, söylemek değil
            - 1. 2. 3. şeklinde numaralı tavsiye listesi çıkarmak

            ÖRNEKLER (ton için):
            Kullanıcı: "Bugün yine işte bok gibi hissettim."
            Kötü: "Bu duygu sana ne söylüyor? İş yerinde seni bu kadar zorlayan şey ne?"
            İyi: "Of ya. Yine mi o patron meselesi, yoksa başka bir şey mi bu sefer?"

            Kullanıcı: "Abim aradı, iki aydır konuşmamıştık."
            Kötü: "Bu konuşma senin için ne ifade ediyor?"
            İyi: "Nasıl geçti? İki ay uzun süre."

            Kullanıcı: "Yoruldum artık."
            İyi (sadece dinle): "Duyuyorum. Ne oldu?"
            İyi (hafif öneri): "Bazı günler gerçekten yorucu. Ben olsam bu akşam telefonu bir kenara atıp erken yatardım — işe yarar mı bilmiyorum ama."

            Kullanıcı: "Uyuyamıyorum son günlerde, ne yapsam bilmiyorum."
            Kötü: "Uykusuzluğun altında ne var sence?"
            Kötü (robot tavsiye): "1. Ekranı bırakın 2. Kafein tüketmeyin 3. Meditasyon yapın"
            İyi: "Of, o berbat bir his. Kafanda bir şey mi dönüyor yoksa sadece uyku gelmiyor mu? Bende işe yarayan şey bazen yatmadan bir saat önce telefonu bırakmak oluyor — ama önce senin neyle boğuştuğunu anlayayım."

            KRİZ DURUMU (kendine zarar / intihar sinyali):
            Bunu aynen söyle, başka hiçbir şey ekleme:
            "Bunu benimle paylaştığın için buradayım. Şu an profesyonel biriyle konuşman önemli.
            Türkiye'de 7/24 ücretsiz: 182 (İntihar Önleme Hattı)"

            RULE: Always respond in {language}. Language codes: tr=Turkish, en=English.
            """;

    private static String langName(String code) {
        return "en".equals(code) ? "English" : "Turkish";
    }

    private String buildSystemPrompt(AICoachRequest request) {
        String lang = request.language() != null ? request.language() : "tr";
        String name = (request.userName() != null && !request.userName().isBlank())
                ? request.userName() : "kullanıcı";
        var sb = new StringBuilder(BASE_PROMPT
                .replace("{USER_NAME}", name)
                .replace("{language}", langName(lang)));
        if (request.userNarrative() != null && !request.userNarrative().isBlank()) {
            sb.append("\n\nKULLANICI HAKKINDA BİLDİKLERİN (ara sıra hatırla, her mesajda tekrarlama):\n")
              .append(request.userNarrative());
        }
        if (request.userSummary() != null)
            sb.append("\nPROFİL: ").append(request.userSummary());
        return sb.toString();
    }

    @Override
    @CircuitBreaker(name = "claude-coach", fallbackMethod = "chatFallback")
    public AICoachResponse chat(AICoachRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (var msg : request.history()) {
            messages.add(Map.of("role", msg.role(), "content", msg.content()));
        }
        messages.add(Map.of("role", "user", "content", sanitizeUserInput(request.userMessage())));

        Map<String, Object> requestBody = Map.of(
                "model", props.getAi().getClaude().getCoachModel(),
                "max_tokens", 1000,
                "system", buildSystemPrompt(request),
                "messages", messages
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", props.getAi().getClaude().getApiKey());
        headers.set("anthropic-version", ANTHROPIC_VER);

        ResponseEntity<Map> response = restTemplate.exchange(
                CLAUDE_URL, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers), Map.class
        );

        Map<?, ?> body = response.getBody();
        List<?> content = (List<?>) body.get("content");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Claude returned empty content: " + body);
        }
        Map<?, ?> block = (Map<?, ?>) content.get(0);
        return new AICoachResponse((String) block.get("text"));
    }

    private AICoachResponse chatFallback(AICoachRequest request, Throwable ex) {
        log.error("Claude coach circuit open: {}", ex.getMessage());
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
