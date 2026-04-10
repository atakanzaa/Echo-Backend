CREATE TABLE subscriptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    original_transaction_id     VARCHAR(100) NOT NULL UNIQUE,
    product_id                  VARCHAR(100) NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    purchase_date               TIMESTAMPTZ NOT NULL,
    expires_date                TIMESTAMPTZ NOT NULL,
    grace_period_expires_date   TIMESTAMPTZ,
    auto_renew_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    auto_renew_product_id       VARCHAR(100),
    environment                 VARCHAR(20) NOT NULL DEFAULT 'Production',
    latest_receipt              TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_status
    ON subscriptions(status);

CREATE INDEX idx_subscriptions_active_expires
    ON subscriptions(expires_date)
    WHERE status IN ('ACTIVE', 'GRACE_PERIOD');

CREATE TABLE feature_limits (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier        VARCHAR(20) NOT NULL,
    feature_key VARCHAR(50) NOT NULL,
    max_value   INTEGER NOT NULL,
    period      VARCHAR(20),
    UNIQUE(tier, feature_key)
);

INSERT INTO feature_limits (tier, feature_key, max_value, period)
VALUES
    ('FREE', 'JOURNAL_ENTRIES', 8, 'MONTHLY'),
    ('FREE', 'COACH_SESSIONS', 2, 'MONTHLY'),
    ('FREE', 'COACH_MESSAGES_PER_SESSION', 6, 'SESSION'),
    ('FREE', 'COACH_MESSAGES_TOTAL', 12, 'MONTHLY'),
    ('FREE', 'INSIGHTS_MAX_PERIOD', 7, NULL),
    ('FREE', 'SUMMARY_MAX_PERIOD', 7, NULL),
    ('FREE', 'ACTIVE_GOALS', 3, NULL),
    ('FREE', 'ACTIVE_TIME_CAPSULES', 1, NULL),
    ('FREE', 'SYNTHESIS_INTERVAL', 20, NULL),
    ('PREMIUM', 'JOURNAL_ENTRIES', -1, 'MONTHLY'),
    ('PREMIUM', 'COACH_SESSIONS', -1, 'MONTHLY'),
    ('PREMIUM', 'COACH_MESSAGES_PER_SESSION', 30, 'SESSION'),
    ('PREMIUM', 'COACH_MESSAGES_TOTAL', 300, 'MONTHLY'),
    ('PREMIUM', 'INSIGHTS_MAX_PERIOD', 365, NULL),
    ('PREMIUM', 'SUMMARY_MAX_PERIOD', 365, NULL),
    ('PREMIUM', 'ACTIVE_GOALS', -1, NULL),
    ('PREMIUM', 'ACTIVE_TIME_CAPSULES', 10, NULL),
    ('PREMIUM', 'SYNTHESIS_INTERVAL', 10, NULL);

CREATE TABLE usage_counters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    feature_key     VARCHAR(50) NOT NULL,
    period_start    DATE NOT NULL,
    usage_count     INTEGER NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, feature_key, period_start)
);

CREATE INDEX idx_usage_counters_lookup
    ON usage_counters(user_id, feature_key, period_start);

CREATE TABLE subscription_events (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id             UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type                  VARCHAR(50) NOT NULL,
    original_transaction_id     VARCHAR(100),
    product_id                  VARCHAR(100),
    environment                 VARCHAR(20),
    raw_payload                 JSONB,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscription_events_user_created
    ON subscription_events(user_id, created_at DESC);
