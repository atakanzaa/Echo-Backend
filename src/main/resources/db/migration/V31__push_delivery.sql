-- Push delivery tracking + per-token APNs environment.
-- Phase 1 of the real push notification rollout.

ALTER TABLE push_tokens
    ADD COLUMN environment   VARCHAR(16)  NOT NULL DEFAULT 'PRODUCTION',
    ADD COLUMN last_used_at  TIMESTAMPTZ,
    ADD COLUMN failure_count INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN app_version   VARCHAR(32);

ALTER TABLE push_tokens
    ALTER COLUMN platform TYPE VARCHAR(16);

UPDATE push_tokens SET platform = UPPER(platform);

ALTER TABLE push_tokens
    ALTER COLUMN platform SET DEFAULT 'IOS';

ALTER TABLE push_tokens
    ADD CONSTRAINT push_tokens_platform_check
        CHECK (platform IN ('IOS'));

ALTER TABLE push_tokens
    ADD CONSTRAINT push_tokens_environment_check
        CHECK (environment IN ('PRODUCTION', 'SANDBOX'));

CREATE INDEX idx_push_tokens_user_active
    ON push_tokens(user_id)
    WHERE is_active = TRUE;

CREATE TABLE notification_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    push_token_id   UUID REFERENCES push_tokens(id) ON DELETE SET NULL,
    status          VARCHAR(32) NOT NULL,
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    apns_id         VARCHAR(64),
    error_code      VARCHAR(64),
    error_reason    TEXT,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(notification_id, push_token_id)
);

CREATE INDEX idx_notification_deliveries_notification
    ON notification_deliveries(notification_id);

CREATE INDEX idx_notification_deliveries_status_created
    ON notification_deliveries(status, created_at DESC);
