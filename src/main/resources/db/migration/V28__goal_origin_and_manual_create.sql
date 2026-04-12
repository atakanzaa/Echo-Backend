ALTER TABLE goals
    ADD COLUMN IF NOT EXISTS creation_type VARCHAR(20) NOT NULL DEFAULT 'AI',
    ADD COLUMN IF NOT EXISTS completion_type VARCHAR(20);

UPDATE goals
SET creation_type = 'AI'
WHERE creation_type IS NULL;

UPDATE goals
SET completion_type = CASE
    WHEN completed_at IS NULL THEN NULL
    WHEN completed_source_type = 'MANUAL' THEN 'MANUAL'
    ELSE 'AI'
END
WHERE completed_at IS NOT NULL
  AND completion_type IS NULL;
