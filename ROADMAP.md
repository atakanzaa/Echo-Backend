# Echo — Backend Roadmap & Best Practices

> Spring Boot + PostgreSQL | Java 21 | Sıfırdan kurulum rehberi

---

## Proje Yapısı

```
echo-backend/
├── src/main/java/com/echo/
│   ├── EchoApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── AIConfig.java            ← Aktif provider'ı seçer (provider=openai|gemini|claude)
│   │   ├── StorageConfig.java
│   │   └── AsyncConfig.java
│   ├── domain/
│   │   ├── user/User.java
│   │   ├── journal/JournalEntry.java
│   │   ├── journal/AnalysisResult.java
│   │   └── token/RefreshToken.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── JournalEntryRepository.java
│   │   └── AnalysisResultRepository.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── JournalService.java
│   │   ├── TranscriptionService.java   ← AITranscriptionProvider'ı kullanır
│   │   ├── AnalysisService.java        ← AIAnalysisProvider'ı kullanır
│   │   ├── SummaryService.java         ← Dönemsel özet (7/14/30/90/180/365 gün)
│   │   └── StorageService.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── JournalController.java
│   │   └── SummaryController.java
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── security/
│   │   ├── JwtTokenProvider.java
│   │   └── JwtAuthenticationFilter.java
│   ├── ai/                             ← Tüm AI kodları burada, service'ler bilmez
│   │   ├── AITranscriptionProvider.java    (interface)
│   │   ├── AIAnalysisProvider.java         (interface)
│   │   ├── AIAnalysisRequest.java          (record)
│   │   ├── AIAnalysisResponse.java         (record)
│   │   ├── openai/
│   │   │   ├── OpenAITranscriptionProvider.java
│   │   │   └── OpenAIAnalysisProvider.java
│   │   ├── gemini/
│   │   │   ├── GeminiTranscriptionProvider.java
│   │   │   └── GeminiAnalysisProvider.java
│   │   └── claude/
│   │       ├── ClaudeTranscriptionProvider.java  (Claude transkript desteklemez → exception fırlatır)
│   │       └── ClaudeAnalysisProvider.java
│   └── exception/
│       └── GlobalExceptionHandler.java
│
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       ├── V1__create_users.sql
│       ├── V2__create_journal_entries.sql
│       ├── V3__create_analysis_results.sql
│       └── V4__create_refresh_tokens.sql
│
└── pom.xml
```

---

## pom.xml — Bağımlılıklar

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.3.0</spring-boot.version>
</properties>

<dependencies>
    <!-- Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- JPA + PostgreSQL -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.3</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Flyway (DB migration) -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- AWS S3 -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3</artifactId>
        <version>2.25.0</version>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## application.yml — Tek Dosya, Hardcoded Değer Yasak

Tek bir `application.yml` var. `dev/prod` profili yok. Tüm değerler ortam değişkeninden (`${VAR}`) okunur.

### ❌ Yanlış
```java
String apiKey = "sk-abc123...";
String dbUrl  = "jdbc:postgresql://localhost:5432/echo";
long expiry   = 900000;
```

### ✅ Doğru
```yaml
spring:
  application:
    name: echo-backend

  datasource:
    url:      ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

  jpa:
    hibernate:
      ddl-auto: validate       # Flyway yönetir, Hibernate schema değiştirmez
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false        # N+1 sorunu önler

  flyway:
    enabled: true
    locations: classpath:db/migration

  servlet:
    multipart:
      max-file-size: ${MAX_AUDIO_SIZE:50MB}
      max-request-size: ${MAX_AUDIO_SIZE:50MB}

app:
  jwt:
    secret:                        ${JWT_SECRET}
    access-token-expiry-seconds:   ${JWT_ACCESS_EXPIRY:900}
    refresh-token-expiry-seconds:  ${JWT_REFRESH_EXPIRY:2592000}

  # Hangi AI provider aktif? Sadece bu satırı değiştir.
  ai:
    provider: ${AI_PROVIDER:openai}     # openai | gemini | claude
    timeout-seconds: ${AI_TIMEOUT:60}
    openai:
      api-key:          ${OPENAI_API_KEY:}
      transcribe-model: ${OPENAI_TRANSCRIBE_MODEL:whisper-1}
      analysis-model:   ${OPENAI_ANALYSIS_MODEL:gpt-4o}
    gemini:
      api-key:          ${GEMINI_API_KEY:}
      analysis-model:   ${GEMINI_ANALYSIS_MODEL:gemini-1.5-pro}
    claude:
      api-key:          ${CLAUDE_API_KEY:}
      analysis-model:   ${CLAUDE_ANALYSIS_MODEL:claude-opus-4-6}

  storage:
    type:       ${STORAGE_TYPE:local}
    local-path: ${LOCAL_STORAGE_PATH:/tmp/echo-audio}
    s3-bucket:  ${S3_BUCKET_NAME:}
    aws-region: ${AWS_REGION:eu-central-1}

  async:
    core-pool-size:  ${ASYNC_CORE_POOL:4}
    max-pool-size:   ${ASYNC_MAX_POOL:8}
    queue-capacity:  ${ASYNC_QUEUE:100}
```

**`.env` dosyası (git'e ekleme! .gitignore'a ekle):**
```
DATABASE_URL=jdbc:postgresql://localhost:5432/echo_dev
DATABASE_USERNAME=echo_user
DATABASE_PASSWORD=echo_pass
JWT_SECRET=buraya-openssl-rand-base64-64-ciktisi
AI_PROVIDER=openai
OPENAI_API_KEY=sk-...
STORAGE_TYPE=local
```

Provider değiştirmek için sadece `.env` içinde `AI_PROVIDER=gemini` yaz. Tek başka değişiklik yok.

---

## AI Abstraction Layer

### AITranscriptionProvider.java (interface)

```java
package com.echo.ai;

public interface AITranscriptionProvider {

    /**
     * Ses dosyasını yazıya çevirir.
     * @param audioBytes Ham ses verisi (M4A/WAV)
     * @param filename   Orijinal dosya adı (uzantı ile birlikte)
     * @return Transkript metni
     */
    String transcribe(byte[] audioBytes, String filename);
}
```

### AIAnalysisProvider.java (interface)

```java
package com.echo.ai;

public interface AIAnalysisProvider {

    /**
     * Transkripti analiz eder, yapılandırılmış sonuç döner.
     * @param request Transkript + kullanıcı bağlamı
     * @return Analiz sonucu
     */
    AIAnalysisResponse analyze(AIAnalysisRequest request);
}
```

### AIAnalysisRequest.java (record)

```java
package com.echo.ai;

public record AIAnalysisRequest(
    String transcript,
    String userTimezone    // İleride "yerel saat sabah mı akşam mı?" gibi bağlam için
) {}
```

### AIAnalysisResponse.java (record)

```java
package com.echo.ai;

import java.util.List;

public record AIAnalysisResponse(
    String summary,
    double moodScore,           // 0.0 - 1.0
    String moodLabel,           // very_positive | positive | neutral | negative | very_negative
    List<String> topics,
    String reflectiveQuestion,
    List<String> keyEmotions,
    String energyLevel          // low | medium | high
) {}
```

### AIConfig.java — Provider seçimi burada yapılır

```java
package com.echo.config;

@Configuration
public class AIConfig {

    private final AppProperties props;

    public AIConfig(AppProperties props) {
        this.props = props;
    }

    @Bean
    public AITranscriptionProvider transcriptionProvider(
            OpenAITranscriptionProvider openai,
            GeminiTranscriptionProvider gemini) {

        return switch (props.getAi().getProvider()) {
            case "openai"  -> openai;
            case "gemini"  -> gemini;
            case "claude"  -> throw new IllegalStateException(
                "Claude ses transkripsiyonu desteklemez. " +
                "Transkripsiyon için AI_PROVIDER=openai veya gemini kullan."
            );
            default -> throw new IllegalStateException(
                "Bilinmeyen AI provider: " + props.getAi().getProvider() +
                ". Geçerli değerler: openai, gemini, claude"
            );
        };
    }

    @Bean
    public AIAnalysisProvider analysisProvider(
            OpenAIAnalysisProvider openai,
            GeminiAnalysisProvider gemini,
            ClaudeAnalysisProvider claude) {

        return switch (props.getAi().getProvider()) {
            case "openai"  -> openai;
            case "gemini"  -> gemini;
            case "claude"  -> claude;
            default -> throw new IllegalStateException(
                "Bilinmeyen AI provider: " + props.getAi().getProvider()
            );
        };
    }
}
```

### OpenAITranscriptionProvider.java

```java
package com.echo.ai.openai;

@Component
public class OpenAITranscriptionProvider implements AITranscriptionProvider {

    private final AppProperties props;
    private final RestTemplate restTemplate;

    private static final String WHISPER_URL =
        "https://api.openai.com/v1/audio/transcriptions";

    public OpenAITranscriptionProvider(AppProperties props) {
        this.props = props;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String transcribe(byte[] audioBytes, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(audioBytes) {
            @Override public String getFilename() { return filename; }
        });
        body.add("model", props.getAi().getOpenai().getTranscribeModel());
        body.add("response_format", "json");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(props.getAi().getOpenai().getApiKey());

        ResponseEntity<Map> response = restTemplate.exchange(
            WHISPER_URL, HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class
        );

        return (String) response.getBody().get("text");
    }
}
```

### OpenAIAnalysisProvider.java

```java
package com.echo.ai.openai;

@Component
public class OpenAIAnalysisProvider implements AIAnalysisProvider {

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String CHAT_URL =
        "https://api.openai.com/v1/chat/completions";

    // GPT'nin her seferinde aynı JSON şemasını döndürmesi için system prompt
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
          "energy_level": "<low|medium|high>"
        }
        """;

    @Override
    public AIAnalysisResponse analyze(AIAnalysisRequest request) {
        Map<String, Object> requestBody = Map.of(
            "model", props.getAi().getOpenai().getAnalysisModel(),
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

    private String extractContent(Map body) {
        List choices = (List) body.get("choices");
        Map choice = (Map) choices.get(0);
        Map message = (Map) choice.get("message");
        return (String) message.get("content");
    }

    private AIAnalysisResponse parseResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new AIAnalysisResponse(
                node.get("summary").asText(),
                node.get("mood_score").asDouble(),
                node.get("mood_label").asText(),
                objectMapper.convertValue(node.get("topics"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                node.get("reflective_question").asText(),
                objectMapper.convertValue(node.get("key_emotions"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                node.get("energy_level").asText()
            );
        } catch (Exception e) {
            throw new RuntimeException("GPT yanıtı parse edilemedi: " + json, e);
        }
    }
}
```

### GeminiAnalysisProvider.java (iskelet — API key hazır olunca doldurulur)

```java
package com.echo.ai.gemini;

@Component
public class GeminiAnalysisProvider implements AIAnalysisProvider {

    private final AppProperties props;

    @Override
    public AIAnalysisResponse analyze(AIAnalysisRequest request) {
        // Gemini REST API entegrasyonu buraya gelecek
        // props.getAi().getGemini().getApiKey() ile API key'e ulaş
        throw new UnsupportedOperationException("Gemini analiz henüz implementa edilmedi");
    }
}
```

### ClaudeAnalysisProvider.java (iskelet)

```java
package com.echo.ai.claude;

@Component
public class ClaudeAnalysisProvider implements AIAnalysisProvider {

    private final AppProperties props;

    @Override
    public AIAnalysisResponse analyze(AIAnalysisRequest request) {
        // Anthropic Messages API entegrasyonu buraya gelecek
        // props.getAi().getClaude().getApiKey() ile API key'e ulaş
        throw new UnsupportedOperationException("Claude analiz henüz implementa edilmedi");
    }
}
```

### Service'ler provider'ı doğrudan enjekte eder — provider ismini bilmez

```java
@Service
public class AnalysisService {

    private final AIAnalysisProvider analysisProvider;   // ← interface, OpenAI/Gemini/Claude değil
    private final AITranscriptionProvider transcriptionProvider;

    // Spring, AIConfig'de seçilen implementasyonu inject eder
    public AnalysisService(AIAnalysisProvider analysisProvider,
                           AITranscriptionProvider transcriptionProvider) {
        this.analysisProvider = analysisProvider;
        this.transcriptionProvider = transcriptionProvider;
    }

    public AIAnalysisResponse analyze(String transcript, String timezone) {
        return analysisProvider.analyze(new AIAnalysisRequest(transcript, timezone));
    }

    public String transcribe(byte[] audioBytes, String filename) {
        return transcriptionProvider.transcribe(audioBytes, filename);
    }
}
```

---

## AppProperties.java — Tüm Config Tek Yerden

```java
@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    @Valid private Jwt jwt = new Jwt();
    @Valid private AI ai = new AI();
    @Valid private Storage storage = new Storage();
    @Valid private Async async = new Async();

    public static class Jwt {
        @NotBlank private String secret;
        @Positive private long accessTokenExpirySeconds;
        @Positive private long refreshTokenExpirySeconds;
        // getters/setters
    }

    public static class AI {
        @NotBlank private String provider = "openai";
        @Positive private int timeoutSeconds = 60;
        private OpenAI openai = new OpenAI();
        private Gemini gemini = new Gemini();
        private Claude claude = new Claude();

        public static class OpenAI {
            private String apiKey;
            private String transcribeModel = "whisper-1";
            private String analysisModel = "gpt-4o";
            // getters/setters
        }

        public static class Gemini {
            private String apiKey;
            private String analysisModel = "gemini-1.5-pro";
            // getters/setters
        }

        public static class Claude {
            private String apiKey;
            private String analysisModel = "claude-opus-4-6";
            // getters/setters
        }
        // getters/setters
    }

    public static class Storage {
        private String type = "local";
        private String localPath = "/tmp/echo-audio";
        private String s3Bucket;
        private String awsRegion;
        // getters/setters
    }

    public static class Async {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 100;
        // getters/setters
    }

    // getters/setters for top-level fields
}
```

---

## Veritabanı Şeması

### V1__create_users.sql
```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    timezone        VARCHAR(50) NOT NULL DEFAULT 'UTC',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_users_email ON users(email);
```

### V2__create_journal_entries.sql
```sql
CREATE TYPE entry_status AS ENUM (
    'uploading', 'transcribing', 'analyzing', 'complete', 'failed'
);

CREATE TABLE journal_entries (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recorded_at             TIMESTAMPTZ NOT NULL,
    entry_date              DATE NOT NULL,
    audio_url               TEXT,
    audio_duration_seconds  INTEGER,
    transcript              TEXT,
    status                  entry_status NOT NULL DEFAULT 'uploading',
    error_message           TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_journal_entries_user_date ON journal_entries(user_id, entry_date DESC);
-- Partial index: sadece işlenmekte olan kayıtları hızlı bul
CREATE INDEX idx_journal_entries_active ON journal_entries(status)
    WHERE status NOT IN ('complete', 'failed');
```

### V3__create_analysis_results.sql
```sql
CREATE TABLE analysis_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id    UUID NOT NULL UNIQUE REFERENCES journal_entries(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entry_date          DATE NOT NULL,
    summary             TEXT NOT NULL,
    mood_score          DECIMAL(4,3) NOT NULL CHECK (mood_score BETWEEN 0 AND 1),
    mood_label          VARCHAR(20) NOT NULL,
    topics              TEXT[] NOT NULL,
    reflective_question TEXT NOT NULL,
    key_emotions        TEXT[] NOT NULL,
    energy_level        VARCHAR(10) NOT NULL,
    raw_ai_response     JSONB,            -- debug için ham AI yanıtı
    ai_provider         VARCHAR(20),      -- hangi provider ürettiyse kaydet (openai/gemini/claude)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Dönemsel özet sorguları için kritik
CREATE INDEX idx_analysis_user_date ON analysis_results(user_id, entry_date DESC);
```

### V4__create_refresh_tokens.sql
```sql
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
```

---

## REST API Endpointleri

```
AUTH
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/auth/me

JOURNAL
POST   /api/v1/journal/entries           multipart: {audio, recordedAt, durationSeconds}
GET    /api/v1/journal/entries/{id}
GET    /api/v1/journal/entries/{id}/status
GET    /api/v1/journal/entries?date=YYYY-MM-DD
GET    /api/v1/journal/entries/recent?limit=7

SUMMARY  ← Dönemsel özetler
GET    /api/v1/summary?period=7
GET    /api/v1/summary?period=14
GET    /api/v1/summary?period=30
GET    /api/v1/summary?period=90
GET    /api/v1/summary?period=180
GET    /api/v1/summary?period=365
GET    /api/v1/summary?period=30&endDate=2025-01-15   (opsiyonel bitiş tarihi)
```

---

## Async Ses İşleme Pipeline'ı

```
iOS multipart upload
        ↓
POST /api/v1/journal/entries
        → JournalEntry oluştur (status=uploading)
        → {entryId, status} anında döndür
        ↓ (@Async "journalProcessingExecutor")
JournalService.processEntryAsync(entryId)
        ↓
1. StorageService.save()      status=transcribing
        ↓
2. AITranscriptionProvider.transcribe()
                              status=analyzing
        ↓
3. StorageService.delete()    (ses dosyasını hemen sil — gizlilik)
        ↓
4. AIAnalysisProvider.analyze()
5. AnalysisResult kaydet      status=complete
        ↓
(Herhangi bir adımda hata → status=failed, error_message set)
```

---

## Security Best Practices

### JWT

```java
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    public JwtTokenProvider(AppProperties props) {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET en az 32 karakter olmalı. Üret: openssl rand -base64 64"
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = props.getJwt().getAccessTokenExpirySeconds() * 1000L;
    }

    public String generateAccessToken(UUID userId, String email) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
            .signWith(signingKey)
            .compact();
    }

    public Claims validateAndParseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
```

```java
// Şifre: BCrypt strength 12
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

---

## Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_FAILED", String.join(", ", errors)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("İşlenmeyen hata", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "Beklenmeyen bir hata oluştu"));
    }

    public record ErrorResponse(String code, String message) {}
}
```

---

## Dönemsel Özet Sistemi (SummaryService)

### Desteklenen Periyotlar

```java
public enum SummaryPeriod {
    WEEK(7), TWO_WEEKS(14), MONTH(30),
    QUARTER(90), HALF_YEAR(180), YEAR(365);

    private final int days;
    SummaryPeriod(int days) { this.days = days; }
    public int getDays() { return days; }

    public static SummaryPeriod fromDays(int days) {
        return Arrays.stream(values())
            .filter(p -> p.days == days)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Geçersiz periyot: " + days + ". Geçerli: 7, 14, 30, 90, 180, 365"
            ));
    }
}
```

### SummaryController.java

```java
@RestController
@RequestMapping("/api/v1/summary")
public class SummaryController {

    private final SummaryService summaryService;

    @GetMapping
    public ResponseEntity<SummaryResponse> getSummary(
            @RequestParam int period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserPrincipal principal) {

        SummaryPeriod summaryPeriod = SummaryPeriod.fromDays(period);
        LocalDate resolvedEnd = endDate != null ? endDate : LocalDate.now();

        return ResponseEntity.ok(
            summaryService.getSummary(principal.getId(), summaryPeriod, resolvedEnd)
        );
    }
}
```

### iOS'a dönen SummaryResponse JSON şekli

```json
{
  "startDate": "2025-01-01",
  "endDate": "2025-03-31",
  "periodDays": 90,
  "recordedDays": 67,
  "coveragePercent": 74,
  "averageMoodScore": 0.71,
  "highestMoodScore": 0.95,
  "lowestMoodScore": 0.32,
  "moodTrend": "improving",
  "dominantTopics": ["iş", "egzersiz", "uyku", "aile", "proje"],
  "topEmotions": ["motive", "yorgun", "minnettar", "stresli", "mutlu"],
  "energyDistribution": { "high": 23, "medium": 31, "low": 13 },
  "bestDay": "2025-03-15",
  "worstDay": "2025-01-22",
  "narrativeSummary": "Bu çeyrekte genel olarak olumlu bir süreç geçirdin..."
}
```

---

## .gitignore (zorunlu)

```
# Secrets
.env
*.pem
*.key

# Build
target/
*.jar

# IDE
.idea/
*.iml
.vscode/

# Local ses dosyaları
/tmp/echo-audio/
```

---

## Hızlı Başlangıç

```bash
# PostgreSQL (Docker)
docker run --name echo-db \
  -e POSTGRES_DB=echo_dev \
  -e POSTGRES_USER=echo_user \
  -e POSTGRES_PASSWORD=echo_pass \
  -p 5432:5432 -d postgres:16

# JWT Secret üret
openssl rand -base64 64

# Projeyi başlat (.env dosyası yüklüyse)
./mvnw spring-boot:run
```

---

## Geliştirme Sırası

- [ ] **Faz 1** — Spring Boot kurulum, AppProperties, Flyway migration'ları, Auth (register/login/JWT)
- [ ] **Faz 2** — JournalEntry, StorageService (local), AITranscriptionProvider (OpenAI), async pipeline
- [ ] **Faz 3** — AIAnalysisProvider (OpenAI), AnalysisResult, polling endpoint
- [ ] **Faz 4** — SummaryService (7/14/30/90/180/365), SummaryController
- [ ] **Faz 5** — S3 entegrasyonu, rate limiting, /health endpoint
