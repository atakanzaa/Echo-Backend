-- Per-user notification preferences (master toggle + per-category + preferred hour + timezone).
-- Default row backfilled for every existing user.

CREATE TABLE notification_preferences (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    master_enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    daily_reminders_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    community_enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    achievements_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    insights_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    mood_alerts_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    capsules_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    coach_enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    system_enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    preferred_local_hour        INTEGER NOT NULL DEFAULT 20
                                  CHECK (preferred_local_hour BETWEEN 0 AND 23),
    quiet_hours_start_local     INTEGER
                                  CHECK (quiet_hours_start_local IS NULL OR quiet_hours_start_local BETWEEN 0 AND 23),
    quiet_hours_end_local       INTEGER
                                  CHECK (quiet_hours_end_local IS NULL OR quiet_hours_end_local BETWEEN 0 AND 23),
    timezone                    VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Backfill defaults for existing users so the scheduler can pick them up.
INSERT INTO notification_preferences (user_id, timezone)
SELECT id, COALESCE(NULLIF(timezone, ''), 'UTC')
FROM users
ON CONFLICT (user_id) DO NOTHING;

CREATE INDEX idx_notification_preferences_preferred_hour
    ON notification_preferences(preferred_local_hour);

-- Track per-day reminder fan-out so we never send the same reminder twice.
CREATE TABLE notification_reminder_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reminder_type       VARCHAR(64) NOT NULL,
    local_date          DATE NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, reminder_type, local_date)
);

CREATE INDEX idx_notification_reminder_log_date
    ON notification_reminder_log(local_date);
