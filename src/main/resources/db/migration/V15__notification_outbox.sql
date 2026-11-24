-- Notifications are written here after the business transaction commits. A delivery worker
-- (SMTP, push, ...) can later pick up PENDING rows; until then the table is the delivery record.
CREATE TABLE notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('EMAIL')),
    recipient VARCHAR(255) NOT NULL,
    template VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ
);

CREATE INDEX notification_outbox_pending_idx ON notification_outbox (id) WHERE status = 'PENDING';
CREATE INDEX notification_outbox_user_idx ON notification_outbox (user_id, id DESC);
