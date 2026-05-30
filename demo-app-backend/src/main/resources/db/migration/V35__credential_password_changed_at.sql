-- IA-5(1)(d): track when the password was last changed so the system can enforce max age
ALTER TABLE credentials ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();
