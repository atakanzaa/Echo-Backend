CREATE TABLE analysis_results (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id    UUID         NOT NULL UNIQUE REFERENCES journal_entries(id) ON DELETE CASCADE,
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entry_date          DATE         NOT NULL,
    summary             TEXT         NOT NULL,
    mood_score          DECIMAL(4,3) NOT NULL CHECK (mood_score BETWEEN 0 AND 1),
    mood_label          VARCHAR(20)  NOT NULL,
    topics              TEXT[]       NOT NULL,
    reflective_question TEXT         NOT NULL,
    key_emotions        TEXT[]       NOT NULL,
    energy_level        VARCHAR(10)  NOT NULL,
    raw_ai_response     JSONB,
    ai_provider         VARCHAR(20),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_user_date
    ON analysis_results(user_id, entry_date DESC);
