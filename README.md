# Echo Backend

Echo Backend, iOS tarafındaki Echo uygulamasını besleyen Spring Boot tabanlı bir API servisidir. Uygulamanın ana odağı sesli günlük tutma, AI destekli analiz, koçluk oturumları, hedef takibi, community özellikleri, bildirimler ve abonelik yönetimidir.

Backend; PostgreSQL + Flyway ile veri yönetimi, JWT ile kimlik doğrulama, çoklu AI provider desteği, Resend ile e-posta akışları, Cloudflare R2/S3 uyumlu görsel depolama ve Apple StoreKit 2 abonelik doğrulama akışlarını tek bir servis altında toplar.

## Öne Çıkan Yetenekler

- Ses kaydından veya transcript’ten journal entry oluşturma
- Asenkron AI analizi ve durum polling akışı
- AI coach oturumları ve konuşma geçmişi
- Özetler, içgörüler ve kullanıcı profil sentezi
- Goal suggestion, goal acceptance/rejection ve progress takibi
- Time capsule oluşturma ve kilitli içerik akışı
- Community feed, post, yorum, beğeni ve takip sistemi
- Bildirim merkezi ve push token kaydı
- Google login, klasik auth, refresh token ve parola sıfırlama
- Apple StoreKit 2 ile abonelik doğrulama ve restore akışı
- Resend webhook ve e-posta gönderim entegrasyonu
- Yerel disk veya Cloudflare R2 üstünde görsel saklama

## Ürün Kapsamı

Bu servis aşağıdaki domain’leri kapsar:

- Kimlik ve kullanıcı profili
- Journal ve AI analiz boru hattı
- AI coach ve kullanıcı hafıza sentezi
- Goal, achievement ve time capsule
- Community sosyal etkileşimleri
- Notification ve push delivery hazırlığı
- Subscription, entitlement ve quota kontrolü

## Teknoloji Yığını

| Alan | Teknoloji |
|---|---|
| Dil / Runtime | Java 21 |
| Framework | Spring Boot 3.3 |
| Web | Spring MVC |
| Güvenlik | Spring Security + JWT |
| Veri | Spring Data JPA + PostgreSQL 16 |
| Migration | Flyway |
| Rate limiting | Bucket4j |
| Cache | Caffeine |
| Dayanıklılık | Resilience4j |
| Storage | Local disk veya Cloudflare R2 / S3 |
| E-posta | Resend |
| Gözlemlenebilirlik | Spring Actuator + structured JSON logging |
| Build | Maven Wrapper (`./mvnw`) |
| Reverse proxy | Caddy |
| Container | Docker / Docker Compose |

## Mimari Bakış

`graphify-out/GRAPH_REPORT.md` çıktısına göre sistemin en merkezi soyutlamaları `GoalIntegrationService`, `AppleStoreKitService`, `AIProviderRouter`, `CommunityService`, `ResendWebhookService` ve `GoalService`. Bu da projenin yalnızca CRUD API değil, event-driven ve entegrasyon ağırlıklı bir backend olduğunu gösteriyor.

Ana runtime akışı özetle şu şekilde:

1. İstekler `Spring MVC` katmanına gelir.
2. `RequestIdFilter`, `JwtAuthenticationFilter` ve `RateLimitFilter` devreye girer.
3. Controller katmanı ilgili service’e yönlendirir.
4. Service katmanı domain kurallarını uygular, repository katmanını çağırır ve gerekiyorsa event yayınlar.
5. AI işlemleri `AIProviderRouter` üzerinden seçilen sağlayıcıya yönlenir.
6. Veriler PostgreSQL’e yazılır; migration’lar Flyway ile yönetilir.
7. Bazı akışlarda listener, scheduler ve async işlemler devreye girer.

Kod tabanındaki ana katmanlar:

- `controller`: REST API uçları
- `service`: iş kuralları ve orkestrasyon
- `repository`: veri erişimi
- `domain`: JPA entity ve enum’lar
- `dto`: request/response modelleri
- `security`: JWT, rate limit, request ID filtreleri
- `ai`: provider soyutlamaları ve provider implementasyonları
- `event`: domain event’leri ve listener’lar
- `config`: property, storage, security, web ve async konfigürasyonu

## Temel İş Akışları

### 1. Journal analizi

- İstemci ses dosyası veya transcript gönderir.
- Sistem entry’yi oluşturur ve gerekirse quota tüketir.
- AI transcribe/analyze pipeline asenkron tetiklenir.
- Sonuç `analysis_results` tarafına yazılır.
- Gerekli event’ler yayınlanır.
- Goal, achievement, capsule ve notification süreçleri zincir halinde devreye girebilir.

Not: Kod tabanındaki açıklamalara göre ses dosyaları kalıcı olarak saklanmaz; byte akışı AI tarafına bellek üzerinden aktarılır. Depolama katmanı community görselleri içindir.

### 2. AI coach

- Kullanıcı bir coach session başlatır.
- Son mesajlar, ilgili journal context’i ve kullanıcı hafızası toplanır.
- İstek seçili AI provider’a gönderilir.
- Kullanıcı ve asistan mesajları kaydedilir.
- Belirli aralıklarda profile/memory sentezi yapılır.

### 3. Goal ve growth sistemi

- Journal veya coach akışından hedef önerileri çıkarılabilir.
- Kullanıcı önerileri kabul/reddedebilir veya manuel hedef oluşturabilir.
- Tamamlanan hedefler achievement ve notification akışlarını besler.

### 4. Subscription ve entitlement

- Apple signed transaction backend’e gelir.
- `SubscriptionService` doğrular ve aktif eder.
- `EntitlementService` quota ve özellik erişimini hesaplar.
- Apple server notification akışları webhook üzerinden işlenir.

## AI Provider Desteği

Kod tabanında aktif provider implementasyonları:

- OpenAI
- Gemini
- Claude

Uygulama konfigürasyonunda provider seçimi `AI_PROVIDER` ile yapılır. İsteğe bağlı fallback provider da tanımlanabilir.

İlgili bileşenler:

- `AIProviderRouter`
- `OpenAI*Provider`
- `Gemini*Provider`
- `Claude*Provider`

## API Yüzeyi

Base path: `/api/v1`

Başlıca controller grupları:

### Auth

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/google`
- `POST /auth/refresh`
- `POST /auth/logout`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`
- `POST /auth/change-password`
- `GET /auth/me`

### User ve privacy

- `GET /users/me/stats`
- `GET /users/me/profile-summary`
- `GET /users/me/stats/detailed`
- `PATCH /users/me`
- `GET /privacy/consent`
- `PUT /privacy/consent`
- `POST /privacy/delete-account`

### Journal

- `POST /journal/entries` (`multipart/form-data`, audio upload)
- `POST /journal/entries/transcript`
- `GET /journal/entries/{id}`
- `GET /journal/entries/{id}/status`
- `GET /journal/entries?date=YYYY-MM-DD`
- `GET /journal/entries/recent`
- `GET /journal/entries/on-this-day`

### Coach

- `GET /coach/sessions`
- `POST /coach/sessions`
- `GET /coach/sessions/{sessionId}/messages`
- `POST /coach/sessions/{sessionId}/messages`
- `POST /coach/sessions/{sessionId}/end`
- `DELETE /coach/sessions/{sessionId}`

### Summary ve AI insights

- `GET /summary`
- `GET /ai-insights/eligibility`
- `GET /ai-insights`

### Goals, achievements ve capsules

- `GET /goals`
- `GET /goals/all`
- `POST /goals`
- `GET /goals/suggestions`
- `POST /goals/suggestions/{id}/accept`
- `POST /goals/suggestions/{id}/reject`
- `PUT /goals/{id}/complete`
- `PUT /goals/{id}/not-completed`
- `PUT /goals/{id}/dismiss`
- `DELETE /goals/{id}`
- `GET /achievements`
- `GET /capsules`
- `POST /capsules`
- `GET /capsules/{id}`

### Community

- `GET /community/feed`
- `GET /community/posts/{postId}`
- `POST /community/posts` (JSON veya multipart)
- `DELETE /community/posts/{postId}`
- `POST /community/posts/{postId}/like`
- `DELETE /community/posts/{postId}/like`
- `GET /community/posts/{postId}/comments`
- `POST /community/posts/{postId}/comments`
- `DELETE /community/posts/{postId}/comments/{commentId}`
- `POST /community/comments/{commentId}/like`
- `DELETE /community/comments/{commentId}/like`
- `POST /community/users/{userId}/follow`
- `DELETE /community/users/{userId}/follow`

### Notifications

- `GET /notifications`
- `GET /notifications/unread-count`
- `PUT /notifications/{id}/read`
- `PUT /notifications/read-all`
- `POST /notifications/push-token`

### Subscription ve public config

- `GET /subscription/status`
- `POST /subscription/verify`
- `POST /subscription/restore`
- `POST /subscription/apple/notify`
- `GET /app/config`
- `POST /webhooks/resend`
- `GET /health`

## Güvenlik ve Operasyonel Özellikler

- Stateless JWT authentication
- `BCryptPasswordEncoder(12)`
- Method security aktif
- Request bazlı `X-Request-ID` üretimi ve iletimi
- Bucket4j ile katmanlı rate limiting
- Health, liveness ve readiness endpoint’leri
- Structured JSON loglama (`logback-spring.xml`)
- Resilience4j circuit breaker yapılandırmaları
- CORS konfigürasyonu profile göre ayarlanmış durumda
- Flyway ile `ddl-auto: validate` yaklaşımı

Rate limit kuralları kabaca şu şekilde:

- Auth endpoint’leri: dakika başına 5
- Ağır endpoint’ler: dakika başına 30
- Genel API trafiği: dakika başına 120

## Dizin Yapısı

```text
.
├── src/main/java/com/echo
│   ├── ai
│   ├── config
│   ├── controller
│   ├── domain
│   ├── dto
│   ├── event
│   ├── exception
│   ├── repository
│   ├── security
│   └── service
├── src/main/resources
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── db/migration
│   └── prompts
├── docs
├── graphify-out
├── scripts
├── docker-compose.yml
├── docker-compose.prod.yml
├── Dockerfile
└── Caddyfile
```

## Gereksinimler

Lokal geliştirme için:

- Java 21
- PostgreSQL 16
- İsteğe bağlı Docker / Docker Compose
- Seçtiğiniz AI provider için API anahtarı

## Lokal Geliştirme

### 1. Ortam dosyasını hazırlayın

```bash
cp .env.example .env
```

`.env` dosyasını doldurun. En kritik alanlar:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `AI_PROVIDER`
- ilgili provider API anahtarı

Not: Spring Boot bu repoda `.env` dosyasını otomatik yüklemez. Lokal çalıştırmadan önce değişkenleri shell ortamına export etmeniz gerekir.

### 2. PostgreSQL veritabanını hazırlayın

Örnek veritabanı:

- Database: `echo_db`
- User: `postgres` veya `.env` içinde verdiğiniz kullanıcı

### 3. Environment değişkenlerini shell’e yükleyin

```bash
set -a
source .env
set +a
```

### 4. Uygulamayı dev profili ile başlatın

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 5. Health check

```bash
curl http://localhost:8080/api/v1/health
```

## Build ve Test

### Derleme

```bash
./mvnw clean compile
```

### Paketleme

```bash
./mvnw clean package
```

Docker image üretmeden önce `target/echo-backend-1.0.0-SNAPSHOT.jar` oluşmuş olmalıdır; `Dockerfile` bu jar’ı kopyalar.

### Test

```bash
./mvnw test
```

Not: Repodaki runbook içinde test stabilizasyonunun hâlâ geliştirme konusu olduğu belirtilmiş. Bu yüzden üretim öncesi smoke check ve manuel doğrulama da önemlidir.

## Docker ile Çalıştırma

Bu repo daha çok production benzeri dağıtım senaryosunu hedefleyen compose dosyaları içerir.

### 1. Production ortam dosyasını hazırlayın

```bash
cp .env.prod.example .env.prod
```

### 2. Değerleri doldurun

Başlıca alanlar:

- `DB_USER`, `DB_PASS`, `DB_NAME`
- `JWT_SECRET`
- `AI_PROVIDER` ve provider anahtarları
- `DOMAIN`, `ACME_EMAIL`
- gerekiyorsa `R2_*` alanları

### 3. Servisleri ayağa kaldırın

```bash
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

### 4. Durumu kontrol edin

```bash
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml ps
```

Production compose yapısı:

- `app`: Spring Boot servisi
- `db`: PostgreSQL 16
- `caddy`: TLS termination + reverse proxy

## Storage Modları

### Local

- Community görselleri diske yazılır
- Varsayılan dizin: `~/echo-uploads/images`
- Public erişim yolu: `/uploads/images/...`

### S3 / Cloudflare R2

Gerekli alanlar:

- `R2_ENDPOINT`
- `R2_ACCESS_KEY`
- `R2_SECRET_KEY`
- `R2_REGION`
- `R2_IMAGES_BUCKET`
- `R2_IMAGES_PUBLIC_URL`

## Veritabanı ve Migration

- Migration dosyaları `src/main/resources/db/migration` altında tutulur.
- Şu anki şema; kullanıcı, journal, analysis, refresh token, community, achievements, time capsule, coach, notification, subscription ve privacy alanlarını kapsar.
- Hibernate tarafında `ddl-auto: validate` kullanılır; şema değişiklikleri doğrudan entity üzerinden değil Flyway migration ile yapılmalıdır.

## Gözlemlenebilirlik

Başlıca endpoint’ler:

- `GET /api/v1/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /actuator/health`

Management exposure:

- `health`
- `info`
- `metrics`
- `prometheus`

## E-posta ve Webhook Akışları

Resend entegrasyonu aşağıdaki ihtiyaçları kapsar:

- Satın alma onayı e-postaları
- Şifre sıfırlama akışları
- Webhook event işleme
- Gerekli durumlarda email suppression yönetimi

İlgili public endpoint:

- `POST /api/v1/webhooks/resend`

## Yedekleme

`scripts/backup.sh` production PostgreSQL dump alıp Cloudflare R2/S3 hedefine yüklemek için kullanılır.

Örnek kullanım:

```bash
ENV_FILE=/opt/echo/.env.prod /opt/echo/scripts/backup.sh
```

Script’in beklediği kritik değişkenler:

- `DB_USER`
- `R2_ENDPOINT`
- `R2_BACKUP_BUCKET`
- `AWS_ACCESS_KEY_ID` veya `R2_ACCESS_KEY`
- `AWS_SECRET_ACCESS_KEY` veya `R2_SECRET_KEY`

## Faydalı Dokümanlar

- [Architecture map](docs/architecture-map.md)
- [Production runbook](docs/PRODUCTION_RUNBOOK.md)
- [Best practices](docs/BEST_PRACTICES.md)
- [Roadmap](docs/ROADMAP.md)
- [Workflow guide](docs/workflow-guide.md)
- [Lessons learned](docs/lessons.md)

## Geliştirme Notları

- `application-dev.yml` ve `application-prod.yml` profile bazlı ayrılmıştır.
- AI prompt metinleri `src/main/resources/prompts/` altında tutulur.
- `graphify-out/` klasörü kod tabanının bilgi grafı çıktısını içerir.
- Kod tabanında event/listener ve scheduler ağı önemli bir rol oynar; değişiklik yaparken yalnızca controller-service akışına bakmak yeterli olmayabilir.

## Kısa Başlangıç Özeti

Lokal hızlı başlangıç:

```bash
cp .env.example .env
set -a
source .env
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

İlk kontrol:

```bash
curl http://localhost:8080/api/v1/health
```

---

Bu README, repo yapısı ve mevcut kod tabanı esas alınarak hazırlanmıştır. Proje büyüdükçe özellikle endpoint örnekleri, sequence diagram’lar ve deploy checklist bölümleri genişletilebilir.
