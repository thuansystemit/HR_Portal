-- Note: CONCURRENTLY removed — Flyway runs inside a transaction.
-- These plain CREATE INDEX statements are equivalent and run safely in-transaction.

CREATE INDEX idx_users_email
    ON users (lower(email))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_users_status_created
    ON users (status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_rt_token_hash
    ON refresh_tokens (token_hash)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_rt_user_active
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_ph_user_created
    ON password_history (user_id, created_at DESC);

CREATE INDEX idx_credentials_user
    ON credentials (user_id);

CREATE INDEX idx_rp_role
    ON role_permissions (role_id);

CREATE INDEX idx_ur_user
    ON user_roles (user_id);

CREATE INDEX idx_dc_name
    ON document_categories (name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_crv_role_view
    ON category_role_visibility (role_id, can_view)
    WHERE can_view = true;

CREATE INDEX idx_docs_category_uploaded
    ON documents (category_id, uploaded_at DESC)
    WHERE deleted_at IS NULL AND upload_status = 'committed';

CREATE INDEX idx_docs_pending
    ON documents (upload_status, uploaded_at)
    WHERE upload_status = 'pending';

CREATE INDEX idx_docs_uploader
    ON documents (uploaded_by)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_dacl_doc_role
    ON document_acl (document_id, role_id);

CREATE INDEX idx_ae_actor_occurred
    ON audit_events (actor_id, occurred_at DESC);

CREATE INDEX idx_ae_entity
    ON audit_events (entity_type, entity_id, occurred_at DESC);

CREATE INDEX idx_ae_action_occurred
    ON audit_events (action, occurred_at DESC);

CREATE INDEX idx_notif_recipient_unread
    ON notifications (recipient_id, created_at DESC)
    WHERE is_read = false;

CREATE INDEX idx_ps_user
    ON push_subscriptions (user_id);
