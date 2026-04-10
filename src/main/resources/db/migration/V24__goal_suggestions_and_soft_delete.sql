ALTER TABLE goals
    DROP CONSTRAINT IF EXISTS goals_status_check;

ALTER TABLE goals
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_source_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS completed_source_ref_id UUID;

ALTER TABLE goals
    ADD CONSTRAINT goals_status_check
        CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'DISMISSED', 'DELETED'));

CREATE INDEX IF NOT EXISTS idx_goals_user_status_updated_at
    ON goals(user_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS goal_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_id UUID REFERENCES goals(id) ON DELETE SET NULL,
    suggestion_type VARCHAR(30) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_journal_entry_id UUID REFERENCES journal_entries(id) ON DELETE SET NULL,
    source_coach_session_id UUID REFERENCES coach_sessions(id) ON DELETE SET NULL,
    source_coach_message_id UUID REFERENCES coach_messages(id) ON DELETE SET NULL,
    title TEXT,
    timeframe TEXT,
    goal_type VARCHAR(50),
    detected_text TEXT,
    reason TEXT,
    confidence NUMERIC(4, 3),
    dedupe_key VARCHAR(120) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    expires_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_goal_suggestions_user_status_created
    ON goal_suggestions(user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_goal_suggestions_source_journal
    ON goal_suggestions(source_journal_entry_id);

CREATE INDEX IF NOT EXISTS idx_goal_suggestions_source_coach_session
    ON goal_suggestions(source_coach_session_id);

CREATE OR REPLACE FUNCTION update_goal_suggestions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_goal_suggestions_updated_at ON goal_suggestions;

CREATE TRIGGER trg_goal_suggestions_updated_at
    BEFORE UPDATE ON goal_suggestions
    FOR EACH ROW EXECUTE FUNCTION update_goal_suggestions_updated_at();
