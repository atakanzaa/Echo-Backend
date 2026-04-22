# Echo Backend — Roadmap

This document tracks shipped milestones and planned work. Items marked ✅ are in production or fully merged. Items marked 🔄 are in progress. Items without a marker are planned but not yet started.

---

## Shipped

### Core Platform
- ✅ Auth — email/password, Google Sign-In, Apple Sign-In, refresh token rotation
- ✅ JWT with token versioning — access tokens revoked immediately on password change / reset
- ✅ Voice journal pipeline — audio upload, async AI transcription and analysis, status polling
- ✅ Transcript-only path — skip upload for on-device STT results
- ✅ AI coaching — multi-turn sessions, conversation history, ARECE framework prompts
- ✅ User memory synthesis — periodic profile summaries fed into coach context
- ✅ Periodic summaries and AI insights — 7 / 14 / 30 / 90 / 180 / 365 day windows
- ✅ Goal system — AI-suggested and manual goals, accept / reject / complete / dismiss flows
- ✅ Achievements — triggered by goal completion and journal streaks
- ✅ Time capsules — locked content with future reveal date
- ✅ Community — feed, posts (text + image), comments, likes, follows
- ✅ Notifications — in-app notification centre, push token registration
- ✅ Apple StoreKit 2 — JWS verification, server notification webhook, subscription restore
- ✅ Entitlement and quota enforcement
- ✅ Resend integration — transactional email, password reset OTP, webhook processing
- ✅ KVKK / GDPR consent — consent log, account deletion flow

### Infrastructure
- ✅ Multi-provider AI routing — OpenAI · Gemini · Claude, runtime-switchable, with fallback
- ✅ Resilience4j circuit breakers on all AI and external provider calls
- ✅ Virtual threads (Java 21) across all async paths and Tomcat handlers
- ✅ Flyway schema management (V1 – V30)
- ✅ Cloudflare R2 / S3-compatible storage with local-disk fallback
- ✅ Caddy reverse proxy — TLS, CSP, Permissions-Policy, security headers
- ✅ Docker Compose — dev and prod override, port bound to `127.0.0.1`
- ✅ Structured JSON logging, Spring Actuator, Prometheus endpoint
- ✅ PostgreSQL backup script → Cloudflare R2

### Security (Audit — April 2026)
- ✅ Apple StoreKit cert chain pinned to Apple Root CA G3; sandbox JWTs rejected in production
- ✅ Cross-user subscription replay blocked (409 Conflict)
- ✅ Coach session IDOR fixed — journal entry scoped to authenticated user
- ✅ `CF-Connecting-IP` trusted only from verified Cloudflare CIDR ranges
- ✅ `/actuator/**` restricted to `ROLE_ADMIN`
- ✅ Image MIME detection from magic bytes — client-supplied Content-Type ignored
- ✅ CORS fail-closed — wildcard origins rejected at startup
- ✅ Gemini API key moved from URL query param to `x-goog-api-key` request header
- ✅ Google ID token verified locally via JWKS — token never sent as URL parameter
- ✅ Docker hardening — `read_only`, `cap_drop: ALL`, `no-new-privileges` in production

---

## Near-term (Next Release)

### Hybrid E2E Encrypted Journals
Client-side AES-256-GCM encryption for journal transcripts. Backend stores ciphertext only; key never leaves the device. Requires a new migration and a client key-management protocol.

### Push Notification Delivery
Wire up the existing push token store to APNs. Notification events are already modelled and persisted — delivery is the missing link.

### Idempotency Enforcement
`JournalService.createEntry()` already has the idempotency key column (V29). Enforce uniqueness at the service layer to handle iOS retry storms on flaky connections.

### ArchUnit Guardrails
Prevent regressions: forbid direct `findById` calls on user-owned entities in the `service` package; enforce that `ai/` classes are never imported in `controller/`.

### Dependency Maintenance
- Bump Spring Boot 3.3.0 → latest 3.3.x patch
- Enable OWASP Dependency-Check in CI
- Review Nimbus JOSE JWT and Bucket4j versions

---

## Medium-term

### Admin Dashboard API
- User lookup, suspension, and role management
- Subscription override and manual entitlement grant
- Audit log for all admin actions (Flyway migration for `admin_audit_log`)

### Rate Limit Refinement
Combine `(IP, userId, endpoint)` as the composite key for authenticated routes to prevent abuse from logged-in accounts sharing a NAT IP.

### Timing Attack Hardening
`PasswordResetService` — run a dummy BCrypt check on the not-found path to normalise response time and prevent user enumeration via timing.

### Error Code Standardisation
`GlobalExceptionHandler` returns machine-readable error codes in all responses (foundation is in place). Audit all exception sites for consistency; remove free-text messages that leak internal detail.

### Observability Improvements
- Distributed trace IDs propagated to AI provider calls
- Dashboard-ready Grafana provisioning files

---

## Stretch / Backlog

### Multi-device Sync
Journal entries and coach sessions synced across devices. Requires conflict resolution strategy (last-write-wins or CRDT-lite for append-only messages).

### Webhook-driven Goal Progress
Parse Apple Health or third-party webhook events to automatically update goal progress without manual user input.

### On-device AI Fallback
Route to a local Ollama instance when external providers are unreachable. Provider skeleton exists; production routing and circuit-breaker integration needed.

### Community Moderation
Report flow, admin review queue, automated content flagging via AI classifier.

### Analytics Pipeline
Opt-in usage events → time-series store → aggregated product metrics. No PII in the pipeline.
