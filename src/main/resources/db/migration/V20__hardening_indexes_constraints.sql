-- TimeCapsule: add source_journal_entry_id for idempotency
ALTER TABLE time_capsules
    ADD COLUMN source_journal_entry_id UUID REFERENCES journal_entries(id);

CREATE UNIQUE INDEX idx_time_capsules_source
    ON time_capsules(user_id, source_journal_entry_id)
    WHERE source_journal_entry_id IS NOT NULL;

-- Goal: unique constraint for event idempotency
CREATE UNIQUE INDEX idx_goals_source_title
    ON goals(user_id, source_journal_entry_id, title)
    WHERE source_journal_entry_id IS NOT NULL;

-- UserProfileSummary: optimistic locking version column
ALTER TABLE user_profile_summaries
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- PostLikeRepository optimization
CREATE INDEX idx_post_likes_user ON post_likes(user_id);
