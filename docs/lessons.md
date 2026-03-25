# Lessons Learned — Backend

Sık yapılan hataları ve çözümlerini buraya ekliyoruz.
Her ekleme şu formatı takip etsin: **Ne oldu → Neden oldu → Nasıl düzeltildi → Kural**.

---

## 1. `@CreationTimestamp` Sonrası `createdAt` Null Geliyor

**Tarih:** 2026-03-02
**Dosyalar:** `CommunityPost.java`, `CommunityPostResponse.java`, `CommunityService.java`

### Ne Oldu?

Post oluşturulurken 500 Internal Server Error aldık:

```
NullPointerException: Cannot invoke "OffsetDateTime.toString()"
because "CommunityPost.getCreatedAt()" is null
  at CommunityPostResponse.from(CommunityPostResponse.java:40)
```

### Neden Oldu?

`CommunityPost.java`'da şu annotation vardı:

```java
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;
```

`@CreationTimestamp` (Hibernate), timestamp'i veritabanına **INSERT SQL çalıştığı anda** oluşturur.
Ancak Spring Data JPA'nın `repository.save()` metodu veritabanından tekrar okuma (refresh) yapmaz —
`save()` çağrısından önce belleğe aldığı **aynı Java nesnesini** geri döndürür.

Sonuç: `save()` döndükten sonra Java nesnesi üzerinde `createdAt` hâlâ `null`.
`CommunityPostResponse.from()` içinde `post.getCreatedAt().toString()` dediğimizde NPE patlıyor.

```
save() çağrısı
    → Hibernate SQL'e INSERT çalıştırıyor (DB'de createdAt doldu)
    → Ama Java nesnesinde createdAt hâlâ null
    → from() metodunda NPE
```

### Nasıl Düzeltildi?

**Asıl fix — `@PrePersist` eklendi (`CommunityPost.java`):**

```java
@PrePersist
protected void onCreate() {
    if (createdAt == null) createdAt = OffsetDateTime.now();
    if (updatedAt == null) updatedAt = OffsetDateTime.now();
}
```

`@PrePersist` JPA standardıdır. Entity persist edilmeden **önce** çalışır ve değeri doğrudan
Java nesnesine yazar. Yani `save()` döndüğünde `createdAt` artık null değil.

**Savunma katmanı — null-safe guard eklendi (`CommunityPostResponse.java`):**

```java
// Eski (tehlikeli):
post.getCreatedAt().toString()

// Yeni (güvenli):
post.getCreatedAt() != null
    ? post.getCreatedAt().toString()
    : OffsetDateTime.now().toString()
```

### Altın Kural

> **`@CreationTimestamp` / `@UpdateTimestamp` kullanıyorsan, yanına mutlaka `@PrePersist` / `@PreUpdate` ekle.**
> Bu annotationlar DB seviyesinde çalışır; `save()` sonrasında Java nesnesinde değer yok.
> `@PrePersist` ise Java nesnesine yazar — DB'ye gitmeden önce, refresh gerekmeden.

### Alternatif Yöntemler (Neden Seçmedik?)

| Yöntem | Sorun |
|--------|-------|
| `saveAndFlush()` + `entityManager.refresh()` | Gereksiz ekstra DB sorgusu |
| `@EnableJpaAuditing` + `@CreatedDate` | Spring Auditing config gerektirir, proje buna hazır değil |
| Null-check'i sadece response'a eklemek | Root cause'u çözmüyor; DB'de de null kalıyor |

---

## 2. DTO'da `.toString()` Çağrısı Hiçbir Zaman Null-Check'siz Yapılmaz

**Kural:** Bir entity alanını `String`'e çevirirken, o alanın veritabanından null dönüp dönmeyeceğini
her zaman sorgula. `nullable = false` olsa bile Java nesnesi o anı göremiyor olabilir (yukarıdaki derse bak).

```java
// KÖTÜ — Her zaman NPE riski taşır:
entity.getSomeDate().toString()

// İYİ — Null-safe:
entity.getSomeDate() != null ? entity.getSomeDate().toString() : ""
```

> **Not:** Aynı hata `PostComment.java` + `PostCommentResponse.java`'da da yaşandı (2026-03-02).
> `@CreationTimestamp` kullanan **her entity**'de `@PrePersist` olduğundan emin ol.

---

## 3. Backend 204 No Content → iOS Tarafında Decode Hatası

**Tarih:** 2026-03-02
**Dosya (iOS):** `APIClient.swift`

### Ne Oldu?

Like/unlike butonuna basılıyor, UI anlık olarak güncelleniyordu ama hemen eski haline dönüyordu.

### Neden Oldu?

Backend like/unlike endpoint'leri başarı durumunda **204 No Content** döndürür — body yok.

`APIClient.handleResponse()` içinde:
```swift
return try APIClient.decoder.decode(T.self, from: data)  // data = boş Data()
```

Boş `Data()` geçerli JSON değildir. `JSONDecoder` hata fırlatır →
`CommunityViewModel.toggleLike` bunu `catch`'e alır → rollback yapar → UI geri döner.

### Nasıl Düzeltildi?

```swift
// Eski:
return try APIClient.decoder.decode(T.self, from: data)

// Yeni:
let body = data.isEmpty ? Data("{}".utf8) : data
return try APIClient.decoder.decode(T.self, from: body)
```

Boş body geldiğinde `{}` kullanılır; `APIVoidResponse {}` başarıyla decode edilir.

### Altın Kural

> **Backend'den void/boş dönebilecek her endpoint için `APIClient` boş body'yi handle etmeli.**
> `204 No Content` standart bir HTTP status'tur; client bunu decode hatası olarak yorumlamamalıdır.
> iOS tarafında `APIVoidResponse` kullanan tüm çağrılar bu tek değişiklikten otomatik yararlanır.

---

## 4. Async Task Race Condition — Tab/Sayfa Geçişlerinde Eski Sonuç Yazar

**Tarih:** 2026-03-02
**Dosya (iOS):** `CommunityViewModel.swift`

### Ne Oldu?

Following tabına geçilip hemen Global'e dönüldüğünde postlar kayboluyor.

### Neden Oldu?

```
1. loadFeed(tab: "following") → Task A başlar
2. loadFeed(tab: "global")   → selectedTab = "global", posts = [], Task B başlar
3. Task B tamamlanır → posts = [global posts] ✓
4. Task A tamamlanır → posts = [] (kimse takip edilmiyor) ✗ — global postlar silindi!
```

Task A, tamamlandığında `selectedTab` artık "global"'dir ama sonucunu yine de yazar.

### Nasıl Düzeltildi?

```swift
Task {
    do {
        let paged = try await ...
        guard self.selectedTab == activeTab else { return }  // ← stale guard
        posts = paged.content
        ...
    }
}
```

Task sonuç yazmadan önce `selectedTab`'ın hâlâ aynı olduğunu kontrol eder.
Eğer tab değişmişse sonuç sessizce atılır.

### Altın Kural

> **`Task {}` içinde ağ sonucu yazılmadan önce state'in hâlâ geçerli olduğunu kontrol et.**
> Özellikle liste/tab/sayfa geçişlerinde: `guard self.currentTab == requestedTab else { return }`
> Bu pattern, herhangi bir "stale async result" sorununu çözer.
> Daha güçlü alternatif: önceki Task'ı `.cancel()` ile iptal etmek.

---

## 5. Flyway `ADD COLUMN IF NOT EXISTS` Tip Değişikliğini Uygulamaz

**Tarih:** 2026-03-25
**Dosyalar:** `V11__add_privacy_consent.sql`, `V16__add_idempotency_and_dlq.sql`, `V17__fix_idempotency_key_type.sql`, `JournalEntry.java`

### Ne Oldu?

```
SchemaManagementException: Schema-validation: wrong column type encountered
in column [idempotency_key] in table [journal_entries];
found [uuid (Types#OTHER)], but expecting [varchar(64) (Types#VARCHAR)]
```
Uygulama startup'ta crash aldı, hiç ayağa kalkmadı.

### Neden Oldu?

İki farklı migration aynı kolonu tanımladı:

- **V11** → `ADD COLUMN IF NOT EXISTS idempotency_key UUID`
- **V16** → `ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64) UNIQUE`

V16 çalıştığında kolon zaten mevcuttu → PostgreSQL `IF NOT EXISTS` sayesinde
ADD'i sessizce **atladı**. Kolon UUID olarak kaldı.

Hibernate entity'de `@Column(name = "idempotency_key", length = 64)` → `varchar(64)` bekliyordu.
DB `uuid` döndürdü → schema validation hatası.

```
V11 çalıştı  → idempotency_key UUID eklendi
V16 çalıştı  → "kolon var, atlıyorum" (IF NOT EXISTS)
Hibernate    → "ben varchar(64) bekliyorum ama DB uuid diyor" → HATA
```

### Nasıl Düzeltildi?

Yeni bir migration (V17) ile `ALTER COLUMN ... TYPE` kullanıldı:

```sql
-- V17__fix_idempotency_key_type.sql
ALTER TABLE journal_entries
    ALTER COLUMN idempotency_key TYPE VARCHAR(64) USING idempotency_key::text;
```

`USING idempotency_key::text` → mevcut UUID değerlerini string'e dönüştürür, veri kaybolmaz.

### Altın Kural

> **`ADD COLUMN IF NOT EXISTS` sadece kolonu oluşturur — var olanı değiştirmez.**
> Bir kolonun tipini ya da kısıtlamasını değiştirmek istiyorsan:
> - Yeni migration yaz
> - `ALTER TABLE ... ALTER COLUMN ... TYPE` kullan
> - `USING` ile tip dönüşümünü belirt

### Kontrol Listesi

Yeni migration yazmadan önce:
1. Aynı tablo/kolon adını kullanan önceki migration'ları tara
2. Kolon zaten varsa `ADD COLUMN` yerine `ALTER COLUMN TYPE` yaz
3. `spring.jpa.hibernate.ddl-auto=validate` ile local'de test et — startup'ta anında yakalar

### Flyway + Hibernate İlişkisi (Temel)
- Flyway, uygulama startup'ında **önce** çalışır, migration'ları sırayla uygular.
- Hibernate, Flyway bittikten **sonra** `ddl-auto: validate` ile DB şemasını kontrol eder.
- Validation sadece **kolon tiplerini ve null constraint'lerini** kontrol eder — index'leri görmezden gelir.
- `ddl-auto: validate` production için doğru seçim: ne siler ne değiştirir, sadece uyumsuzlukta durur.

---

## 6. Java Wildcard `Map<?, ?>` ile `getOrDefault` Kullanılamaz

**Tarih:** 2026-03-25
**Dosya:** `GeminiClient.java`

### Ne Oldu?

```
incompatible types: int cannot be converted to capture#1 of ?
```

### Neden Oldu?

```java
Map<?, ?> usage = ...;
int cached = toInt(usage.getOrDefault("cachedContentTokenCount", 0));
//                                                               ^ HATA
```

`Map<?, ?>` wildcard tipi, compiler'ın value tipi hakkında hiçbir bilgisi yoktur.
`getOrDefault(K key, V defaultValue)` metodunda `V` parametresi de `capture#1 of ?` olur.
`int` literal (0) bu capture'a assign edilemez → compile hatası.

### Nasıl Düzeltildi?

```java
// KÖTÜ:
int cached = toInt(usage.getOrDefault("cachedContentTokenCount", 0));

// İYİ:
Object cachedRaw = usage.get("cachedContentTokenCount");
int cached = toInt(cachedRaw != null ? cachedRaw : 0);
```

`get()` her zaman `Object` döndürür → wildcard kısıtı yok.

### Altın Kural

> **`Map<?, ?>` ile `getOrDefault`, `put`, `putIfAbsent` gibi value alan metotlar çalışmaz.**
> Sadece `get()`, `containsKey()`, `keySet()`, `entrySet()` gibi value yazmayan metotlar kullanılabilir.
> Default değer gereken her yerde `get()` + null check yap.

### Ne Zaman `Map<?, ?>` Kullanılır?
- `RestTemplate.exchange(..., Map.class)` ile gelen JSON response'larını cast ederken.
- Bu map'ten sadece **okuma** yapacaksan `Map<?, ?>` güvenli.
- Değer yazacaksan veya tipi biliyorsan `Map<String, Object>` kullan.

---

## 7. `@Transactional` Self-Invocation Tuzağı

**Tarih:** 2026-03-25
**Dosyalar:** `JournalService.java`, `JournalEntryUpdater.java`

### Ne Oldu?

`@Async` ile işaretlenmiş `analyzeAsync()` metodu async çalışmıyor, ana thread'i bloke ediyor.

### Neden Oldu?

```java
@Service
public class JournalService {

    @Transactional
    public void createEntry(...) {
        analyzeAsync(entry); // BU ÇAĞRI @Async VE @Transactional PROXY'İ ATLAR
    }

    @Async
    @Transactional
    public void analyzeAsync(JournalEntry entry) { ... }
}
```

Spring, `@Async` ve `@Transactional`'ı bir **proxy sınıfı** oluşturarak uygular.
`this.analyzeAsync(...)` çağrısı proxy'yi değil, doğrudan nesneyi hedef alır →
annotation'lar etkisiz kalır.

### Nasıl Düzeltildi?

Async metodu ayrı bir `@Component` bean'e taşındı:

```java
@Component
public class JournalEntryUpdater {
    @Async
    @Transactional
    public void analyzeAsync(JournalEntry entry) { ... }
}

@Service
public class JournalService {
    private final JournalEntryUpdater updater;

    public void createEntry(...) {
        updater.analyzeAsync(entry); // Proxy üzerinden gider → @Async çalışır
    }
}
```

### Altın Kural

> **`@Async`, `@Transactional`, `@Cacheable` gibi Spring proxy annotation'larını
> aynı class içinden çağırma.** Her zaman başka bir bean üzerinden çağır.
> Bu, Spring'de en sık yapılan ve en az fark edilen hatalardan biridir.

---

## 8. Resilience4j: Tek Global Circuit Breaker Tüm Operasyonları Bozar

**Tarih:** 2026-03-25
**Dosyalar:** `application.yml`, tüm AI provider'lar

### Ne Oldu?

Gemini analysis operasyonu rate limit'e takıldı → circuit breaker açıldı →
aynı CB'yi kullanan coach ve transcription da anında `ServiceUnavailableException` fırlattı.

### Neden Oldu?

Tüm provider metodları `@CircuitBreaker(name = "ai-provider")` kullanıyordu.
Tek CB → bir operasyondaki yüksek hata oranı herkesi etkiler.
Ayrıca `OllamaCoachProvider` yanlışlıkla `"gemini-coach"` adını kullanıyordu:
Ollama'nın hatası Gemini'nin CB sayacını tetikliyordu (cross-provider contamination).

### Nasıl Düzeltildi?

Her provider + operasyon kombinasyonu için ayrı CB instance:

```yaml
resilience4j.circuitbreaker.instances:
  gemini-coach:         { slidingWindowSize: 10, failureRateThreshold: 50 }
  gemini-analysis:      { slidingWindowSize: 20, failureRateThreshold: 40 }
  gemini-synthesis:     { slidingWindowSize: 10, failureRateThreshold: 50 }
  gemini-transcription: { slidingWindowSize: 15, failureRateThreshold: 40 }
  openai-coach:         { slidingWindowSize: 10, failureRateThreshold: 50 }
  openai-analysis:      { slidingWindowSize: 20, failureRateThreshold: 40 }
  claude-coach:         { slidingWindowSize: 10, failureRateThreshold: 50 }
  claude-analysis:      { slidingWindowSize: 20, failureRateThreshold: 40 }
```

```java
@CircuitBreaker(name = "gemini-analysis", fallbackMethod = "fallback")
public AIAnalysisResponse analyze(...) { ... }
```

### Altın Kural

> **CB adı convention'ı: `{provider}-{operation}`**
> Her provider+operasyon kombinasyonu kendi CB instance'ını kullanır →
> bir provider'ın sorunu diğerini etkilemez.
> Farklı gecikme/hata profiline sahip operasyonlar için ayrı eşikler belirle
> (coach 12s, analysis 30s, synthesis 45s gibi).

---

## 9. Gemini API Response JSON Truncation → `JsonEOFException` → Pipeline Silent Failure

**Tarih:** 2026-03-25
**Dosya:** `GeminiAnalysisProvider.java`

### Ne Oldu?

1 dakikalık ses kaydedildi, analiz endpoint'i çağrıldı.
Log'larda hiçbir sonuç görünmedi. Kullanıcıya hata dönmedi ama entry asla tamamlanmadı.

### Neden Oldu?

```
Prompt → Gemini API → response JSON ~3570 char'da kesildi
→ JsonEOFException (parse hatası)
→ @CircuitBreaker fallback devreye girdi
→ ServiceUnavailableException
→ Async task sessizce başarısız oldu
→ Entry PROCESSING state'de takılı kaldı
```

Kesilmenin nedeni: `goals` array'indeki her item için model ~50 kez aynı uzun cümleyi tekrar etti.
`maxOutputTokens` kısıtı yoktu → JSON tokenlar dolunca kesildi.

### Nasıl Düzeltildi?

1. `responseSchema`'ya `maxItems` ve `maxLength` eklendi:
```json
{
  "type": "ARRAY",
  "maxItems": 3,
  "items": { "type": "STRING", "maxLength": 80 }
}
```

2. `generationConfig`'e `maxOutputTokens: 1024` eklendi.

3. System instruction'a few-shot örnekler eklendi — model çıktı formatını öğrenir, token israf etmez.

### Altın Kural

> **LLM çıktısı her zaman kesilebilir. `responseSchema` + `maxOutputTokens` olmadan production'a alma.**
> JSON truncation genellikle sessiz hata üretir — direkt parse exception değil, downstream timeout'a dönüşür.
> Her AI çağrısını structured logging ile izle: token count, latency, cost.

---

## 10. Lombok + `@Qualifier` Uyumsuzluğu

**Tarih:** 2026-03-25
**Dosyalar:** Tüm AI provider sınıfları (`OpenAI*Provider`, `Claude*Provider` vb.)

### Ne Oldu?

Tüm OpenAI ve Claude provider'ları operation-specific RestTemplate yerine
primary (60s timeout) RestTemplate'i inject ediyordu.
Coach gibi kullanıcının beklediği operasyonlarda da 60s timeout aktifti.

### Neden Oldu?

```java
@RequiredArgsConstructor
public class OpenAICoachProvider {
    @Qualifier("coachRestTemplate") // Bu çalışmaz!
    private final RestTemplate restTemplate;
}
```

Lombok'un ürettiği constructor `@Qualifier` annotationını **taşımaz**.
Spring doğru bean'i inject edemez, `@Primary` olanı (60s timeout) alır.

### Nasıl Düzeltildi?

`@RequiredArgsConstructor` kaldırıldı, manual constructor yazıldı:

```java
public class OpenAICoachProvider {
    private final RestTemplate restTemplate;

    public OpenAICoachProvider(AppProperties props,
                               @Qualifier("coachRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
}
```

### Altın Kural

> **`@Qualifier` kullanılacak field'larda `@RequiredArgsConstructor` kullanma.**
> Lombok annotation'ı constructor parametresine kopyalamaz.
> `@Qualifier` gereken her constructor'ı manuel yaz.

---

## 11. AI Prompt Tasarımı — Echo Tekrarı Sorunu

**Tarih:** 2026-03-25
**Dosya:** `GeminiCoachProvider.java`, `coach-system.txt`

### Ne Oldu?

Model şu pattern'e girdi:
```
Kullanıcı: "X yaptım, iyi hissettim"
Model: "X yaptığını ve iyi hissettiğini duymak harika. X sana ne ifade ediyor?"
```
Kullanıcı kendi yazdığını aynen geri okudu — annoying, robotik.

### Neden Oldu?

ARECE çerçevesi ("Kabul Et → Yansıt") doğru uygulandığında değerlidir,
ama prompt yeterince spesifik değildi — model "Yansıt" adımını
kelimesi kelimesine tekrar olarak yorumladı.

### Nasıl Düzeltildi?

1. **Yansıtma ≠ Tekrar**: Kullanıcının söylediklerinin *arkasındaki şeyi* yakala.
2. **Boş onay yasağı**: "Bu çok güzel", "Harika" → kaldır.
3. **Tek soru**: En fazla 1 derin soru; evet/hayır ile geçiştirilemeyecek türden.
4. **Negatif örnekler**: Promptta "şunu yapma" + "yanlış örnek + neden yanlış" bölümleri modeli kalibre eder.
5. **4 katmanlı analiz**: Trigger → Meaning → Need → Avoidance (içsel reasoning, dışa yazılmaz).

### Altın Kural

> **Promptlara sadece ne yapılacağını değil, ne yapılmayacağını da yaz.**
> Modele somut "yanlış örnek" vermek, soyut kurallardan çok daha etkilidir.
> Kalibrasyon örneği ekle — model davranışı bu örneğe göre hizalanır.

---

## 12. Transparent Fallback Pattern — AI Provider Router

**Tarih:** 2026-03-25
**Dosya:** `AIProviderRouter.java`

### Ne Oldu?

Primary AI provider (Gemini) circuit breaker açıldığında kullanıcı direkt HTTP 503 görüyordu.

### Neden Oldu?

`AIProviderRouter` sadece primary provider'ı döndürüyordu.
CB açıldığında `ServiceUnavailableException` direkt kullanıcıya ulaşıyordu.

### Nasıl Düzeltildi?

Router her `resolve*()` metodunda primary'yi bir lambda wrapper'a sardı:

```java
private AICoachProvider wrappedCoach(AICoachProvider primary, String name) {
    return request -> {
        try {
            return primary.chat(request);
        } catch (ServiceUnavailableException e) {
            log.warn("ai_fallback op=COACH primary={} switching_to=openai", name);
            return openAICoach.chat(request);
        }
    };
}
```

Lambda Spring bean değil — ama `primary.chat(request)` çağrısı
concrete bean'in Spring AOP proxy'sine gidiyor → `@CircuitBreaker` hâlâ aktif.

### Fallback Davranışı

| Durum | Sonuç |
|---|---|
| Primary CB kapalı | Primary yanıt verir |
| Primary CB açık | `ai_fallback` log → OpenAI devreye girer, kullanıcı fark etmez |
| Her iki CB açık | `ServiceUnavailableException` → HTTP 503 |
| `provider=openai` | Wrapper yok, direkt OpenAI |

### Altın Kural

> **Fallback'i servislere değil router'a koy.**
> `CoachService`, `JournalService` gibi çağıran kod hiç değişmez.
> `ServiceUnavailableException` → fallback provider'a geç.
> Fallback da açıksa exception propagate et → HTTP 503.

---

*Son güncelleme: 2026-03-25*
