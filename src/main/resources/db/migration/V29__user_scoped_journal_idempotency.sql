DROP INDEX IF EXISTS ux_journal_entries_idempotency_key;
DROP INDEX IF EXISTS idx_journal_entries_idempotency_key;

ALTER TABLE journal_entries
    DROP CONSTRAINT IF EXISTS journal_entries_idempotency_key_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_journal_entries_user_idempotency_key
    ON journal_entries (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
