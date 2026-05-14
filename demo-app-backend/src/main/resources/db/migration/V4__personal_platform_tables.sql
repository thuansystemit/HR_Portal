CREATE TABLE user_settings (
    user_id           UUID        PRIMARY KEY,
    theme             VARCHAR(20) NOT NULL DEFAULT 'system'
                          CHECK (theme IN ('light', 'dark', 'system')),
    language          VARCHAR(10) NOT NULL DEFAULT 'en',
    date_format       VARCHAR(30) NOT NULL DEFAULT 'MM/dd/yyyy',
    default_page_size INT         NOT NULL DEFAULT 10
                          CHECK (default_page_size IN (5, 10, 25, 50)),
    notif_email       BOOLEAN     NOT NULL DEFAULT true,
    notif_push        BOOLEAN     NOT NULL DEFAULT false,
    notif_desktop     BOOLEAN     NOT NULL DEFAULT false,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT us_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE notifications (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID         NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT,
    is_read      BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ,

    CONSTRAINT notif_recipient_fk FOREIGN KEY (recipient_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE push_subscriptions (
    id         UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID  NOT NULL,
    endpoint   TEXT  NOT NULL,
    p256dh_key TEXT  NOT NULL,
    auth_key   TEXT  NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ps_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ps_endpoint_uq UNIQUE (endpoint)
);
