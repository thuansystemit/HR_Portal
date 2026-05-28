-- FedRAMP P1-2: Audit log hardening (AU-3, AU-9)
-- Add correlation_id and session_id columns to all audit_events partitions.

ALTER TABLE audit_events
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS session_id     VARCHAR(36);

-- Indexes on the parent table propagate to existing partitions in PG 14+.
-- Create on parent; Postgres will apply to each partition automatically.
CREATE INDEX IF NOT EXISTS idx_audit_correlation ON audit_events (correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_actor       ON audit_events (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_occurred    ON audit_events (occurred_at DESC);

-- Grant INSERT-only to the application user (AU-9: no DELETE/UPDATE on audit log).
-- Replace 'hrportal_app' with your actual DB application username.
-- REVOKE UPDATE, DELETE ON audit_events FROM hrportal_app;
-- (Commented out — requires the role to exist; run manually per environment.)
