ALTER TABLE users
    ADD COLUMN email_suppressed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_suppressed_reason VARCHAR(50),
    ADD COLUMN email_suppressed_at TIMESTAMPTZ;

CREATE TABLE resend_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_message_id VARCHAR(120) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    email_id VARCHAR(120),
    recipient_email VARCHAR(255),
    subject VARCHAR(255),
    created_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    raw_payload JSONB NOT NULL
);

CREATE INDEX idx_resend_webhook_events_email_id
    ON resend_webhook_events(email_id);

CREATE INDEX idx_resend_webhook_events_recipient_created
    ON resend_webhook_events(recipient_email, received_at DESC);
