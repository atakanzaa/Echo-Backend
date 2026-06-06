-- Admin monitoring: durable record of unexpected 5xx errors.
-- Fed by GlobalExceptionHandler; read by the admin dashboard. No stack traces
-- or PII are stored here — full detail stays in structured logs (requestId).

CREATE TABLE system_error_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at     TIMESTAMPTZ NOT NULL,
    request_id      VARCHAR(64),
    user_id         UUID,
    http_status     INTEGER,
    error_code      VARCHAR(64),
    exception_class VARCHAR(160),
    message         VARCHAR(500),
    path            VARCHAR(200),
    method          VARCHAR(8),
    severity        VARCHAR(16) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_system_error_log_occurred_at
    ON system_error_log(occurred_at DESC);
