-- IA-5(1)(h): Maintain a history of credential hashes to prevent password reuse.
-- NIST SP 800-63B 5.1.1.2 requires verifiers to disallow recently used passwords.

CREATE TABLE password_history (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(256) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pw_history_user_created ON password_history (user_id, created_at DESC);
