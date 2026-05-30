-- AU-9: Protect audit log from deletion and modification at the database layer.
-- Defense-in-depth: the application layer (AuditImmutabilityListener, repository overrides)
-- is the first line; this trigger is the last line that catches any direct DB access.
-- Requires PostgreSQL 13+ (trigger on partitioned parent propagates to all partitions).

CREATE OR REPLACE FUNCTION fn_audit_events_immutable()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are immutable — % is not permitted on audit_events (AU-9)', TG_OP;
END;
$$;

-- Trigger on parent table; Postgres 13+ automatically applies to all existing and future partitions.
CREATE TRIGGER trg_audit_events_no_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION fn_audit_events_immutable();

CREATE TRIGGER trg_audit_events_no_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION fn_audit_events_immutable();

-- Grant INSERT-only on audit_events to the application role (AU-9).
-- The REVOKE statements are run here so they execute automatically during migration.
-- Replace 'hrportal_app' with the actual application DB username if different.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hrportal_app') THEN
        REVOKE UPDATE, DELETE ON audit_events FROM hrportal_app;
    END IF;
END
$$;
