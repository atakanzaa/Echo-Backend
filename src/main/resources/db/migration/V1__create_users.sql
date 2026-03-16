CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    timezone        VARCHAR(50)  NOT NULL DEFAULT 'UTC',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    current_streak  INTEGER      NOT NULL DEFAULT 0,
    longest_streak  INTEGER      NOT NULL DEFAULT 0,
    total_entries   INTEGER      NOT NULL DEFAULT 0,
    last_entry_date DATE,
    mood_score_avg  DECIMAL(4,3) DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
