-- Admin rolü
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'USER';

-- AI Memory Layer: Kullanıcı profil özetleri
CREATE TABLE user_profile_summaries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    weekly_digest       TEXT,
    user_profile        TEXT,
    emotional_patterns  TEXT,
    values_strengths    TEXT,
    growth_trajectory   TEXT,
    last_synthesis_at   TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_profile UNIQUE (user_id)
);

CREATE INDEX idx_user_profile_user ON user_profile_summaries(user_id);
