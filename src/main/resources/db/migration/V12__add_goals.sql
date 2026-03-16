-- V12: Hedef takip tablosu
-- AI'nın günlük girişlerinden tespit ettiği kullanıcı hedefleri.
-- Kullanıcı durumu: PENDING → ACTIVE → COMPLETED | DISMISSED

CREATE TABLE goals (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title                   TEXT         NOT NULL,
    timeframe               TEXT,
    goal_type               TEXT         NOT NULL DEFAULT 'general',
    status                  TEXT         NOT NULL DEFAULT 'PENDING'
                                         CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'DISMISSED')),
    source_journal_entry_id UUID         REFERENCES journal_entries(id) ON DELETE SET NULL,
    detected_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goals_user_id
    ON goals(user_id);

CREATE INDEX idx_goals_user_status
    ON goals(user_id, status);

-- updated_at otomatik güncelleme
CREATE OR REPLACE FUNCTION update_goals_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_goals_updated_at
    BEFORE UPDATE ON goals
    FOR EACH ROW EXECUTE FUNCTION update_goals_updated_at();
