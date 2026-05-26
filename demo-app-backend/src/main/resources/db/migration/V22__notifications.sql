CREATE TABLE IF NOT EXISTS notifications (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    recipient_id UUID        NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT,
    is_read      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at      TIMESTAMPTZ
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notifications_unread    ON notifications (recipient_id) WHERE is_read = FALSE;
