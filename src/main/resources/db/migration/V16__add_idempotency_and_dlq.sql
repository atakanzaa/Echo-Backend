-- V16: idempotency key on journal_entries + AI job dead-letter queue

-- Idempotency key: client-generated UUID prevents duplicate submissions on retry / bad network
ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64) UNIQUE;

CREATE INDEX IF NOT EXISTS idx_journal_entries_idempotency_key
    ON journal_entries(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- AI Job Dead Letter Queue
-- Failed AI jobs are moved here after exceeding retry budget.
-- JournalMaintenanceService re-processes entries where attempt_count < 5
-- using exponential backoff: next_retry_at = last_failed_at + (5min * 2^attempt_count).
CREATE TABLE IF NOT EXISTS ai_job_dlq (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id  UUID        NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    job_type          VARCHAR(50) NOT NULL,    -- ANALYSIS | TRANSCRIPTION | SYNTHESIS
    error_code        VARCHAR(50),             -- TIMEOUT | RATE_LIMITED | PARSE_ERROR | SERVER_ERROR
    error_message     TEXT,
    payload           JSONB,                   -- serialized job params for replay
    attempt_count     INT         NOT NULL DEFAULT 1,
    first_failed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_failed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    next_retry_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at       TIMESTAMPTZ,             -- set when successfully replayed or manually closed
    resolution        VARCHAR(50)              -- SUCCESS | ABANDONED | MANUAL
);

CREATE INDEX IF NOT EXISTS idx_dlq_pending
    ON ai_job_dlq(next_retry_at, attempt_count)
    WHERE resolved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_dlq_entry
    ON ai_job_dlq(journal_entry_id);
