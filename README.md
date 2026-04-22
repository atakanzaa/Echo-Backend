# Echo Backend

> AI-powered voice journaling API — Spring Boot 3.3 · Java 21 · PostgreSQL 16

Echo Backend is the server-side API for the Echo iOS app. It handles voice journal ingestion, asynchronous AI analysis, conversational coaching, goal tracking, community interactions, and Apple subscription management — all in a single, lean service optimised for a solo-developer budget.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language / Runtime | Java 21 (Virtual Threads enabled) |
| Framework | Spring Boot 3.3 |
| Security | Spring Security · JWT (Nimbus JOSE JWT) |
| Persistence | Spring Data JPA · PostgreSQL 16 · Flyway |
| Resilience | Resilience4j Circuit Breaker |
| Rate limiting | Bucket4j |
| Cache | Caffeine |
| AI Providers | OpenAI · Gemini · Claude (runtime-switchable) |
| Storage | Local disk or Cloudflare R2 / S3-compatible |
| Email | Resend |
| Reverse proxy | Caddy (TLS termination) |
| Container | Docker · Docker Compose |
| Observability | Spring Actuator · structured JSON logging |

---

## Features

- **Voice journal pipeline** — upload audio or plain transcript; async AI transcription and analysis with status polling
- **AI coaching** — multi-turn conversation sessions backed by user memory synthesis
- **Goal system** — AI-suggested and manual goals, progress tracking, achievements
- **Time capsules** — locked journal content revealed at a future date
- **Community** — feed, posts, comments, likes, follows
- **Notifications** — in-app notification centre and push token registration
- **Auth** — email/password, Google Sign-In, Apple Sign-In, refresh token rotation
- **Subscriptions** — Apple StoreKit 2 JWS verification, entitlement and quota enforcement
- **Summaries & insights** — periodic AI-generated summaries and mood analytics

---

## Architecture

```
iOS Client
    │
    ▼
Caddy (TLS · CSP headers · rate-gate)
    │
    ▼
Spring MVC  ←  RequestIdFilter · JwtAuthenticationFilter · RateLimitFilter
    │
    ▼
Service layer  →  AIProviderRouter  →  OpenAI | Gemini | Claude
    │
    ▼
Repository layer  →  PostgreSQL 16
```

Key packages:

| Package | Responsibility |
|---|---|
| `ai/` | Provider interfaces and all OpenAI / Gemini / Claude implementations |
| `controller/` | REST endpoints |
| `service/` | Business logic and orchestration |
| `domain/` | JPA entities and enums |
| `dto/` | Request / response models |
| `security/` | JWT, rate-limit, request-ID filters, trusted-proxy resolution |
| `config/` | Application properties, CORS, storage, async config |
| `event/` | Domain events and listeners |
| `exception/` | Typed exceptions and `GlobalExceptionHandler` |

Audio bytes are never stored permanently — they are streamed in memory to the AI provider and discarded. The storage layer is used exclusively for community images.

---

## Security

- Stateless JWT authentication with **token versioning** — access tokens are invalidated immediately on password change or reset without a token blacklist
- BCrypt password hashing (strength 12)
- `CF-Connecting-IP` trusted only from verified Cloudflare CIDR ranges (`cloudflare-ips.txt`)
- Apple StoreKit cert chain pinned to bundled Apple Root CA G3; sandbox JWTs rejected in production
- Image MIME type detected from magic bytes — client-supplied `Content-Type` is ignored
- CORS fail-closed — no mapping registered if origin list is absent or contains a wildcard
- Google ID tokens verified locally via JWKS (Nimbus `DefaultJWTProcessor`, 6-hour cache) — token never sent as a URL parameter
- `/actuator/**` restricted to `ROLE_ADMIN`
- App port bound to `127.0.0.1` in Docker; only Caddy reaches the origin

---

## API Overview

Base path: `/api/v1`

| Group | Key Endpoints |
|---|---|
| **Auth** | `POST /auth/register` · `/auth/login` · `/auth/google` · `/auth/refresh` · `/auth/logout` · `/auth/forgot-password` · `/auth/reset-password` · `/auth/change-password` · `GET /auth/me` |
| **User** | `PATCH /users/me` · `GET /users/me/stats` · `GET /users/me/profile-summary` |
| **Privacy** | `GET/PUT /privacy/consent` · `POST /privacy/delete-account` |
| **Journal** | `POST /journal/entries` (multipart audio) · `/journal/entries/transcript` · `GET /{id}` · `/{id}/status` · `/recent` · `/on-this-day` |
| **Coach** | `GET/POST /coach/sessions` · `GET/POST /sessions/{id}/messages` · `POST /sessions/{id}/end` · `DELETE /sessions/{id}` |
| **Insights** | `GET /summary` · `GET /ai-insights` · `GET /ai-insights/eligibility` |
| **Goals** | `GET/POST /goals` · `/goals/suggestions` · `/goals/{id}/complete` · `DELETE /goals/{id}` |
| **Achievements** | `GET /achievements` |
| **Capsules** | `GET/POST /capsules` · `GET /capsules/{id}` |
| **Community** | Feed, posts, comments, likes, follows under `/community/**` |
| **Notifications** | `GET /notifications` · `/notifications/unread-count` · `PUT /{id}/read` · `POST /push-token` |
| **Subscriptions** | `GET/POST /subscription/status` · `/verify` · `/restore` · `POST /apple/notify` |
| **Config** | `GET /app/config` · `GET /health` |
| **Webhooks** | `POST /webhooks/resend` |

Rate limits (approximate): auth endpoints 5 req/min · heavy endpoints 30 req/min · general traffic 120 req/min.

---

## Local Development

### Prerequisites

- Java 21
- PostgreSQL 16
- An API key for at least one AI provider (OpenAI, Gemini, or Claude)

### 1. Configure environment

```bash
cp .env.example .env
# Fill in DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD,
# JWT_SECRET, AI_PROVIDER, and the matching provider API key.
```

Spring Boot does not auto-load `.env`. Export the variables into your shell before running:

```bash
set -a && source .env && set +a
```

### 2. Start the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Verify

```bash
curl http://localhost:8080/api/v1/health
```

---

## Build & Test

```bash
# Compile
./mvnw clean compile

# Run tests
./mvnw test

# Package
./mvnw clean package
# Produces target/echo-backend-*.jar — required before building the Docker image
```

---

## Docker Deployment

```bash
# 1. Prepare production env file
cp .env.prod.example .env.prod   # fill in all required values

# 2. Start all services (app + PostgreSQL + Caddy)
docker compose --env-file .env.prod \
  -f docker-compose.yml -f docker-compose.prod.yml \
  up -d --build

# 3. Check status
docker compose --env-file .env.prod \
  -f docker-compose.yml -f docker-compose.prod.yml ps
```

Required env variables: `DB_USER`, `DB_PASS`, `DB_NAME`, `JWT_SECRET`, `AI_PROVIDER` + provider key, `DOMAIN`, `ACME_EMAIL`. Cloudflare R2 variables are optional if using local storage.

---

## Storage Modes

| Mode | Description |
|---|---|
| `local` (default) | Community images written to `~/echo-uploads/images`; served at `/uploads/images/**` |
| `s3` | Cloudflare R2 or any S3-compatible bucket; requires `R2_ENDPOINT`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`, `R2_REGION`, `R2_IMAGES_BUCKET`, `R2_IMAGES_PUBLIC_URL` |

---

## Database & Migrations

Flyway manages all schema changes. Hibernate runs with `ddl-auto: validate` — never modify schema directly through entities.

Migrations live in `src/main/resources/db/migration`. Current schema covers: users, journal entries, analysis results, refresh tokens, coach sessions and messages, goals, achievements, time capsules, community posts/comments/likes/follows, notifications, subscriptions, entitlements, privacy consent, and AI job DLQ.

---

## Observability

| Endpoint | Access |
|---|---|
| `GET /api/v1/health` | Public |
| `GET /actuator/health/liveness` | Public |
| `GET /actuator/health/readiness` | Public |
| `GET /actuator/health` | Public |
| `GET /actuator/metrics` | ROLE_ADMIN |

Structured JSON logging is configured via `logback-spring.xml`. Resilience4j circuit breaker instances are registered as health indicators.

---

## Backup

`scripts/backup.sh` dumps PostgreSQL and uploads the archive to Cloudflare R2 / S3.

```bash
ENV_FILE=/opt/echo/.env.prod /opt/echo/scripts/backup.sh
```

Required: `DB_USER`, `R2_ENDPOINT`, `R2_BACKUP_BUCKET`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`.

---

## Documentation

- [Architecture map](docs/architecture-map.md)
- [Production runbook](docs/PRODUCTION_RUNBOOK.md)
- [Roadmap](ROADMAP.md)
- [Best practices](docs/BEST_PRACTICES.md)
- [Workflow guide](docs/workflow-guide.md)
