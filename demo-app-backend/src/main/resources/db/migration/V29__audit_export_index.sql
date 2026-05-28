-- CA-7: Index to support date-range audit export queries efficiently.
-- The existing idx_ae_action_occurred covers (action, occurred_at DESC) but a
-- plain occurred_at index is needed when no action filter is supplied.
CREATE INDEX idx_ae_occurred_at ON audit_events (occurred_at DESC);
