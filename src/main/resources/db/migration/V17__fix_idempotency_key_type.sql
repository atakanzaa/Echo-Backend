-- V17: Change idempotency_key column from UUID to VARCHAR(64)
--
-- Root cause: V11 added idempotency_key as UUID. V16 attempted ADD COLUMN IF NOT EXISTS
-- with VARCHAR(64) but was silently skipped (column already existed). Hibernate entity
-- defines it as String/varchar(64) → schema validation fails at startup.
--
-- Resolution: ALTER the existing column to VARCHAR(64) via explicit CAST.
-- All existing UUID values are preserved as their canonical string representation.

ALTER TABLE journal_entries
    ALTER COLUMN idempotency_key TYPE VARCHAR(64) USING idempotency_key::text;
