-- Preferred language for AI output and future UI localization.
-- Default 'tr' preserves existing behavior for all current users.
ALTER TABLE users ADD COLUMN preferred_language VARCHAR(10) NOT NULL DEFAULT 'tr';
