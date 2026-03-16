CREATE TYPE entry_status AS ENUM (
    'uploading', 'transcribing', 'analyzing', 'complete', 'failed'
);

CREATE TABLE journal_entries (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recorded_at             TIMESTAMPTZ  NOT NULL,
    entry_date              DATE         NOT NULL,
    audio_url               TEXT,
    audio_duration_seconds  INTEGER,
    transcript              TEXT,
    status                  entry_status NOT NULL DEFAULT 'uploading',
    error_message           TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_journal_entries_user_date
    ON journal_entries(user_id, entry_date DESC);

CREATE INDEX idx_journal_entries_active
    ON journal_entries(status)
    WHERE status NOT IN ('complete', 'failed');
