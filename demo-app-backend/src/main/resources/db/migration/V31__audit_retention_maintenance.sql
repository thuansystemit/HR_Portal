-- AU-11: Allow scheduled retention purges while preserving AU-9 immutability for all other paths.
-- The trigger checks a session-local GUC set only by AuditRetentionService within its transaction.
-- Any DELETE attempted outside that maintenance window still raises an exception.

CREATE OR REPLACE FUNCTION fn_audit_events_immutable()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- Allow DELETE only when the maintenance GUC is set in this transaction (AU-11 retention job).
    IF TG_OP = 'DELETE' AND current_setting('app.audit_maintenance', true) = 'true' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Audit events are immutable — % is not permitted on audit_events (AU-9)', TG_OP;
END;
$$;
