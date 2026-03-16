CREATE TYPE capsule_status AS ENUM ('sealed', 'unlocked', 'opened');

CREATE TABLE time_capsules (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255),
    content_text    TEXT,
    content_type    VARCHAR(20)    NOT NULL DEFAULT 'text',
    audio_url       TEXT,
    audio_duration  INTEGER,
    sealed_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    unlock_at       TIMESTAMPTZ    NOT NULL,
    opened_at       TIMESTAMPTZ,
    status          capsule_status NOT NULL DEFAULT 'sealed',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_capsules_user   ON time_capsules(user_id, created_at DESC);
CREATE INDEX idx_capsules_unlock ON time_capsules(unlock_at)
    WHERE status = 'sealed';
