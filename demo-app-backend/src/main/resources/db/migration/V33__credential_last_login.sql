-- IA-4(e): track last successful login so the scheduler can identify inactive accounts
ALTER TABLE credentials ADD COLUMN last_login_at TIMESTAMPTZ;
