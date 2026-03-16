-- V11: KVKK/Gizlilik onay alanları ve optimistic locking
-- Kullanıcı verilerinin AI eğitiminde kullanılması için açık rıza (opt-in, default KAPALI)

-- Kullanıcı tablosuna onay ve güvenlik alanları ekle
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS version                     BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ai_training_consent         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ai_training_consent_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS kvkk_explicit_consent       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS kvkk_consent_at             TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS privacy_policy_version      VARCHAR(10),
    ADD COLUMN IF NOT EXISTS account_deletion_requested_at TIMESTAMPTZ;

-- Onay geçmişi — her consent değişikliği burada izlenir (KVKK Madde 11 denetim izi)
CREATE TABLE IF NOT EXISTS user_consent_log (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consent_type VARCHAR(50) NOT NULL,   -- 'ai_training', 'kvkk', 'privacy_policy'
    granted      BOOLEAN     NOT NULL,
    ip_address   VARCHAR(45),
    user_agent   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_consent_log_user_date
    ON user_consent_log(user_id, created_at DESC);

-- Günlük girdilerine idempotency key ekle (çift yükleme koruması)
ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS idempotency_key UUID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_journal_entries_idempotency
    ON journal_entries(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Güvenlik tetikleyici logları (içerik saklanmaz, sadece tetiklendiği)
CREATE TABLE IF NOT EXISTS safety_trigger_log (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id  UUID,
    trigger_type VARCHAR(50) NOT NULL,  -- 'self_harm', 'violence', 'crisis'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_safety_trigger_user
    ON safety_trigger_log(user_id, created_at DESC);
