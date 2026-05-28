-- FedRAMP P0-2: Multi-factor authentication tables (IA-2(1), IA-2(2))

-- MFA fields on the credentials table (co-located for atomic credential checks)
ALTER TABLE credentials
    ADD COLUMN IF NOT EXISTS mfa_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS mfa_secret      TEXT,
    ADD COLUMN IF NOT EXISTS mfa_method      VARCHAR(20)          DEFAULT 'TOTP',
    ADD COLUMN IF NOT EXISTS mfa_enrolled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS mfa_backup_codes JSONB;

-- Short-lived challenge sessions created after password OK, before MFA code entry.
-- TTL enforced at application level (5 minutes); rows cleaned up by expiry scheduler.
CREATE TABLE mfa_pending_sessions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_token TEXT        NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mfa_pending_token  ON mfa_pending_sessions (challenge_token);
CREATE INDEX idx_mfa_pending_user   ON mfa_pending_sessions (user_id);
CREATE INDEX idx_mfa_pending_expiry ON mfa_pending_sessions (expires_at);

-- WebAuthn hardware key credentials (for phishing-resistant IA-2(1) enforcement)
CREATE TABLE webauthn_credentials (
    id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id   BYTEA   NOT NULL UNIQUE,
    public_key_cose BYTEA   NOT NULL,
    sign_count      BIGINT  NOT NULL DEFAULT 0,
    aaguid          UUID,
    display_name    VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at    TIMESTAMPTZ
);

CREATE INDEX idx_webauthn_user ON webauthn_credentials (user_id);
