-- Enables server-side access token revocation without a blacklist.
-- Bumping this column on password-change or reset invalidates all
-- outstanding access tokens for that user at next request.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
