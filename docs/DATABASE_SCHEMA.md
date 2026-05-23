# Database Schema — Demo App

PostgreSQL 16 · Flyway 10 · PgBouncer (transaction-pool mode)

---

## Table of Contents

1. [Entity Overview](#1-entity-overview)
2. [Entity-Relationship Diagram](#2-entity-relationship-diagram)
3. [DDL — IAM Module](#3-ddl--iam-module)
4. [DDL — Content Module](#4-ddl--content-module)
5. [DDL — Personalization & Platform Modules](#5-ddl--personalization--platform-modules)
6. [DDL — Compliance Module](#6-ddl--compliance-module)
7. [Index Plan](#7-index-plan)
8. [Materialized Views](#8-materialized-views)
9. [Flyway Migration Plan](#9-flyway-migration-plan)
10. [Seed Data](#10-seed-data)
11. [Open Questions](#11-open-questions)

---

## 1. Entity Overview

### Module Breakdown

| Module | Tables | Responsibility |
|--------|--------|---------------|
| IAM | `users`, `credentials`, `password_history`, `refresh_tokens`, `roles`, `permissions`, `role_permissions`, `user_roles` | Identity, authentication, authorisation |
| Content | `document_categories`, `category_role_visibility`, `documents`, `document_acl` | File management and per-role access |
| Personalization | `user_settings` | Per-user preferences (theme, locale, etc.) |
| Compliance | `audit_events` | Append-only activity log, monthly-partitioned |
| Platform | `notifications`, `push_subscriptions` | In-app notification fanout |

**Total: 16 tables** (plus auto-created monthly partitions for `audit_events`)

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Primary keys | `UUID` (v4, gen_random_uuid()) | Avoids sequential-scan leakage of row counts; safe to expose in URLs |
| Soft deletes | `deleted_at TIMESTAMPTZ NULL` on `users`, `documents` | Preserves audit chain; hard deletes only for truly ephemeral rows |
| Permission storage | Normalised `permissions` + `role_permissions` join table | Allows dynamic permission sets without schema changes |
| Credential separation | `credentials` separate from `users` | Lets the IAM module evolve auth methods without altering the user projection |
| Password history | `password_history` (last 5) | Enforces password rotation policy |
| Document bytes | Stored in S3/MinIO; only metadata in Postgres | Avoids TOAST bloat; keeps Postgres cache hit-rate high |
| Audit partitioning | Monthly RANGE on `occurred_at` | Enables instant partition-drop for retention; partition pruning on date queries |
| Materialized views | `mv_upload_trend`, `mv_storage_by_category` | Pre-aggregate expensive report queries; refreshed by scheduler |
| PgBouncer compat | No prepared statements (`prepareThreshold=0`) | Required for transaction-pool mode |

---

## 2. Entity-Relationship Diagram

```mermaid
erDiagram
    users {
        uuid id PK
        varchar full_name
        varchar email UK
        varchar role_id FK
        varchar status
        timestamptz created_at
        timestamptz updated_at
        timestamptz deleted_at
    }
    credentials {
        uuid id PK
        uuid user_id FK
        varchar password_hash
        int failed_attempts
        timestamptz locked_until
        timestamptz updated_at
    }
    password_history {
        uuid id PK
        uuid user_id FK
        varchar password_hash
        timestamptz created_at
    }
    refresh_tokens {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        timestamptz expires_at
        timestamptz revoked_at
        varchar ip_address
        varchar user_agent
        timestamptz created_at
    }
    roles {
        uuid id PK
        varchar name UK
        varchar description
        boolean is_builtin
        timestamptz created_at
        timestamptz updated_at
    }
    permissions {
        uuid id PK
        varchar code UK
        varchar description
        varchar category
    }
    role_permissions {
        uuid role_id FK
        uuid permission_id FK
    }
    user_roles {
        uuid user_id FK
        uuid role_id FK
        timestamptz granted_at
        uuid granted_by FK
    }
    document_categories {
        uuid id PK
        varchar name UK
        varchar description
        int document_count
        timestamptz created_at
        timestamptz updated_at
        timestamptz deleted_at
    }
    category_role_visibility {
        uuid category_id FK
        uuid role_id FK
        boolean can_view
        boolean can_upload
        boolean can_delete
    }
    documents {
        uuid id PK
        uuid category_id FK
        varchar name
        varchar mime_type
        bigint size_bytes
        varchar storage_key
        uuid uploaded_by FK
        varchar upload_status
        timestamptz uploaded_at
        timestamptz deleted_at
    }
    document_acl {
        uuid document_id FK
        uuid role_id FK
        boolean can_view
        boolean can_delete
    }
    user_settings {
        uuid user_id PK FK
        varchar theme
        varchar language
        varchar date_format
        int default_page_size
        boolean notif_email
        boolean notif_push
        boolean notif_desktop
        timestamptz updated_at
    }
    audit_events {
        uuid id PK
        uuid actor_id FK
        varchar action
        varchar entity_type
        uuid entity_id
        jsonb before_state
        jsonb after_state
        varchar ip_address
        varchar user_agent
        varchar outcome
        timestamptz occurred_at
    }
    notifications {
        uuid id PK
        uuid recipient_id FK
        varchar title
        text body
        boolean is_read
        timestamptz created_at
        timestamptz read_at
    }
    push_subscriptions {
        uuid id PK
        uuid user_id FK
        text endpoint UK
        text p256dh_key
        text auth_key
        timestamptz created_at
    }

    users ||--o{ credentials : "has one"
    users ||--o{ password_history : "tracks"
    users ||--o{ refresh_tokens : "owns"
    users ||--o{ user_roles : "assigned via"
    users ||--o| user_settings : "configures"
    users ||--o{ audit_events : "actor"
    users ||--o{ documents : "uploads"
    users ||--o{ notifications : "receives"
    users ||--o{ push_subscriptions : "registers"
    roles ||--o{ role_permissions : "grants"
    roles ||--o{ user_roles : "assigned to"
    roles ||--o{ category_role_visibility : "governs"
    roles ||--o{ document_acl : "permits"
    permissions ||--o{ role_permissions : "included in"
    document_categories ||--o{ category_role_visibility : "restricts"
    document_categories ||--o{ documents : "contains"
    documents ||--o{ document_acl : "guarded by"
```

---

## 3. DDL — IAM Module

```sql
-- ─────────────────────────────────────────────
--  IAM: users
-- ─────────────────────────────────────────────
CREATE TABLE users (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(254) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'inactive')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT users_email_uq UNIQUE (email)
);

-- ─────────────────────────────────────────────
--  IAM: credentials
-- ─────────────────────────────────────────────
CREATE TABLE credentials (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    password_hash   VARCHAR(72) NOT NULL,   -- bcrypt max 72 bytes
    failed_attempts INT         NOT NULL DEFAULT 0
                        CHECK (failed_attempts >= 0),
    locked_until    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT credentials_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT credentials_user_uq UNIQUE (user_id)
);

-- ─────────────────────────────────────────────
--  IAM: password_history  (keep last 5 hashes)
-- ─────────────────────────────────────────────
CREATE TABLE password_history (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL,
    password_hash VARCHAR(72) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ph_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
--  IAM: refresh_tokens
-- ─────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,   -- SHA-256 hex of the opaque token
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(512),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT rt_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT rt_token_hash_uq UNIQUE (token_hash)
);

-- ─────────────────────────────────────────────
--  IAM: roles
-- ─────────────────────────────────────────────
CREATE TABLE roles (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    is_builtin  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT roles_name_uq UNIQUE (name)
);

-- ─────────────────────────────────────────────
--  IAM: permissions
-- ─────────────────────────────────────────────
CREATE TABLE permissions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(60) NOT NULL,
    description VARCHAR(255),
    category    VARCHAR(40) NOT NULL DEFAULT 'general',

    CONSTRAINT permissions_code_uq UNIQUE (code)
);

-- ─────────────────────────────────────────────
--  IAM: role_permissions  (join)
-- ─────────────────────────────────────────────
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,

    PRIMARY KEY (role_id, permission_id),

    CONSTRAINT rp_role_fk FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT rp_permission_fk FOREIGN KEY (permission_id)
        REFERENCES permissions (id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
--  IAM: user_roles  (join)
-- ─────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id    UUID        NOT NULL,
    role_id    UUID        NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID,                              -- NULL for seed/bootstrap

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT ur_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ur_role_fk FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT ur_granted_by_fk FOREIGN KEY (granted_by)
        REFERENCES users (id) ON DELETE SET NULL
);
```

---

## 4. DDL — Content Module

```sql
-- ─────────────────────────────────────────────
--  Content: document_categories
-- ─────────────────────────────────────────────
CREATE TABLE document_categories (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(120) NOT NULL,
    description    VARCHAR(500),
    document_count INT          NOT NULL DEFAULT 0 CHECK (document_count >= 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,

    CONSTRAINT dc_name_uq UNIQUE (name)
);

-- ─────────────────────────────────────────────
--  Content: category_role_visibility  (join)
-- ─────────────────────────────────────────────
CREATE TABLE category_role_visibility (
    category_id UUID    NOT NULL,
    role_id     UUID    NOT NULL,
    can_view    BOOLEAN NOT NULL DEFAULT false,
    can_upload  BOOLEAN NOT NULL DEFAULT false,
    can_delete  BOOLEAN NOT NULL DEFAULT false,

    PRIMARY KEY (category_id, role_id),

    CONSTRAINT crv_category_fk FOREIGN KEY (category_id)
        REFERENCES document_categories (id) ON DELETE CASCADE,
    CONSTRAINT crv_role_fk FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
--  Content: documents
-- ─────────────────────────────────────────────
CREATE TABLE documents (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id   UUID         NOT NULL,
    name          VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(127) NOT NULL DEFAULT 'application/octet-stream',
    size_bytes    BIGINT       NOT NULL DEFAULT 0 CHECK (size_bytes >= 0),
    storage_key   VARCHAR(512) NOT NULL,          -- S3/MinIO object key
    uploaded_by   UUID,
    upload_status VARCHAR(20)  NOT NULL DEFAULT 'pending'
                      CHECK (upload_status IN ('pending', 'committed', 'failed')),
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,

    CONSTRAINT doc_category_fk FOREIGN KEY (category_id)
        REFERENCES document_categories (id) ON DELETE RESTRICT,
    CONSTRAINT doc_uploader_fk FOREIGN KEY (uploaded_by)
        REFERENCES users (id) ON DELETE SET NULL
);

-- ─────────────────────────────────────────────
--  Content: document_acl  (fine-grained overrides)
-- ─────────────────────────────────────────────
CREATE TABLE document_acl (
    document_id UUID    NOT NULL,
    role_id     UUID    NOT NULL,
    can_view    BOOLEAN NOT NULL DEFAULT false,
    can_delete  BOOLEAN NOT NULL DEFAULT false,

    PRIMARY KEY (document_id, role_id),

    CONSTRAINT dacl_doc_fk FOREIGN KEY (document_id)
        REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT dacl_role_fk FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE CASCADE
);
```

---

## 5. DDL — Personalization & Platform Modules

```sql
-- ─────────────────────────────────────────────
--  Personalization: user_settings
-- ─────────────────────────────────────────────
CREATE TABLE user_settings (
    user_id          UUID        PRIMARY KEY,
    theme            VARCHAR(20) NOT NULL DEFAULT 'system'
                         CHECK (theme IN ('light', 'dark', 'system')),
    language         VARCHAR(10) NOT NULL DEFAULT 'en',
    date_format      VARCHAR(30) NOT NULL DEFAULT 'MM/dd/yyyy',
    default_page_size INT        NOT NULL DEFAULT 10
                         CHECK (default_page_size IN (5, 10, 25, 50)),
    notif_email      BOOLEAN     NOT NULL DEFAULT true,
    notif_push       BOOLEAN     NOT NULL DEFAULT false,
    notif_desktop    BOOLEAN     NOT NULL DEFAULT false,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT us_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
--  Platform: notifications
-- ─────────────────────────────────────────────
CREATE TABLE notifications (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID        NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT,
    is_read      BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ,

    CONSTRAINT notif_recipient_fk FOREIGN KEY (recipient_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────
--  Platform: push_subscriptions  (Web Push API)
-- ─────────────────────────────────────────────
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
```

---

## 6. DDL — Compliance Module

```sql
-- ─────────────────────────────────────────────
--  Compliance: audit_events  (partitioned)
--  Monthly RANGE partitioning on occurred_at.
--  Partition name convention: audit_events_YYYY_MM
-- ─────────────────────────────────────────────
CREATE TABLE audit_events (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    actor_id    UUID,                              -- NULL for system events
    action      VARCHAR(80)  NOT NULL,             -- e.g. USER_CREATED
    entity_type VARCHAR(60)  NOT NULL,             -- e.g. User, Document
    entity_id   UUID,
    before_state JSONB,
    after_state  JSONB,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(512),
    outcome     VARCHAR(20)  NOT NULL DEFAULT 'success'
                    CHECK (outcome IN ('success', 'failure')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ae_actor_fk FOREIGN KEY (actor_id)
        REFERENCES users (id) ON DELETE SET NULL
) PARTITION BY RANGE (occurred_at);

-- Initial partitions — extend monthly via cron / Flyway migration
CREATE TABLE audit_events_2026_05
    PARTITION OF audit_events
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE audit_events_2026_06
    PARTITION OF audit_events
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE audit_events_2026_07
    PARTITION OF audit_events
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE TABLE audit_events_2026_08
    PARTITION OF audit_events
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

-- Primary key must include the partition key
ALTER TABLE audit_events
    ADD PRIMARY KEY (id, occurred_at);
```

---

## 7. Index Plan

```sql
-- ═══════════════════════════════════════════════════════
--  IAM indexes
-- ═══════════════════════════════════════════════════════

-- Login lookup (hot path)
CREATE INDEX CONCURRENTLY idx_users_email
    ON users (lower(email))
    WHERE deleted_at IS NULL;

-- Active user list for user-management pages
CREATE INDEX CONCURRENTLY idx_users_status_created
    ON users (status, created_at DESC)
    WHERE deleted_at IS NULL;

-- Refresh-token validation (called on every non-login request)
CREATE INDEX CONCURRENTLY idx_rt_token_hash
    ON refresh_tokens (token_hash)
    WHERE revoked_at IS NULL;

-- Revoke all tokens for a user on logout/deactivation
CREATE INDEX CONCURRENTLY idx_rt_user_active
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

-- Password-history check (up to 5 recent hashes per user)
CREATE INDEX CONCURRENTLY idx_ph_user_created
    ON password_history (user_id, created_at DESC);

-- Credential lockout check
CREATE INDEX CONCURRENTLY idx_credentials_user
    ON credentials (user_id);

-- Permission cache rebuild: fetch all permissions for a role
CREATE INDEX CONCURRENTLY idx_rp_role
    ON role_permissions (role_id);

-- User → role resolution
CREATE INDEX CONCURRENTLY idx_ur_user
    ON user_roles (user_id);

-- ═══════════════════════════════════════════════════════
--  Content indexes
-- ═══════════════════════════════════════════════════════

-- Category list (active, by name)
CREATE INDEX CONCURRENTLY idx_dc_name
    ON document_categories (name)
    WHERE deleted_at IS NULL;

-- Visibility filter: which categories can a role see?
CREATE INDEX CONCURRENTLY idx_crv_role_view
    ON category_role_visibility (role_id, can_view)
    WHERE can_view = true;

-- Document list within a category (most recent first)
CREATE INDEX CONCURRENTLY idx_docs_category_uploaded
    ON documents (category_id, uploaded_at DESC)
    WHERE deleted_at IS NULL AND upload_status = 'committed';

-- Find pending (uncommitted) uploads to garbage-collect
CREATE INDEX CONCURRENTLY idx_docs_pending
    ON documents (upload_status, uploaded_at)
    WHERE upload_status = 'pending';

-- Uploader reference (audit / cascade queries)
CREATE INDEX CONCURRENTLY idx_docs_uploader
    ON documents (uploaded_by)
    WHERE deleted_at IS NULL;

-- ACL lookup: can role_id view/delete this document?
CREATE INDEX CONCURRENTLY idx_dacl_doc_role
    ON document_acl (document_id, role_id);

-- ═══════════════════════════════════════════════════════
--  Compliance (audit_events) indexes — applied per partition
-- ═══════════════════════════════════════════════════════

-- Actor activity timeline
CREATE INDEX CONCURRENTLY idx_ae_actor_occurred
    ON audit_events (actor_id, occurred_at DESC);

-- Entity history (e.g. all events for a specific document)
CREATE INDEX CONCURRENTLY idx_ae_entity
    ON audit_events (entity_type, entity_id, occurred_at DESC);

-- Action-based filtering (e.g. all LOGIN_FAILED events)
CREATE INDEX CONCURRENTLY idx_ae_action_occurred
    ON audit_events (action, occurred_at DESC);

-- ═══════════════════════════════════════════════════════
--  Platform indexes
-- ═══════════════════════════════════════════════════════

-- Unread notification count (badge)
CREATE INDEX CONCURRENTLY idx_notif_recipient_unread
    ON notifications (recipient_id, created_at DESC)
    WHERE is_read = false;

-- Push subscription lookup per user
CREATE INDEX CONCURRENTLY idx_ps_user
    ON push_subscriptions (user_id);
```

---

## 8. Materialized Views

```sql
-- ═══════════════════════════════════════════════════════
--  mv_upload_trend
--  Pre-aggregated monthly upload counts per category
--  for the Upload Trend chart (Reports page).
--  Refresh: daily at 02:00 UTC (low-traffic window).
-- ═══════════════════════════════════════════════════════
CREATE MATERIALIZED VIEW mv_upload_trend AS
SELECT
    dc.id                                           AS category_id,
    dc.name                                         AS category_name,
    date_trunc('month', d.uploaded_at)              AS month,
    count(*)                                        AS upload_count
FROM documents d
JOIN document_categories dc ON dc.id = d.category_id
WHERE d.deleted_at    IS NULL
  AND d.upload_status = 'committed'
GROUP BY dc.id, dc.name, date_trunc('month', d.uploaded_at)
WITH DATA;

-- Unique index required for REFRESH CONCURRENTLY
CREATE UNIQUE INDEX mv_upload_trend_uq
    ON mv_upload_trend (category_id, month);

-- ═══════════════════════════════════════════════════════
--  mv_storage_by_category
--  Total bytes stored per category for the Storage chart.
--  Refresh: daily at 02:05 UTC.
-- ═══════════════════════════════════════════════════════
CREATE MATERIALIZED VIEW mv_storage_by_category AS
SELECT
    dc.id                                           AS category_id,
    dc.name                                         AS category_name,
    count(d.id)                                     AS document_count,
    coalesce(sum(d.size_bytes), 0)                  AS total_bytes
FROM document_categories dc
LEFT JOIN documents d
       ON d.category_id   = dc.id
      AND d.deleted_at    IS NULL
      AND d.upload_status = 'committed'
WHERE dc.deleted_at IS NULL
GROUP BY dc.id, dc.name
WITH DATA;

CREATE UNIQUE INDEX mv_storage_by_category_uq
    ON mv_storage_by_category (category_id);
```

### Refresh Strategy

```sql
-- Run via Spring @Scheduled (or pg_cron) — safe at 10k concurrent because
-- REFRESH CONCURRENTLY does not take an exclusive lock.
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_upload_trend;
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_storage_by_category;
```

---

## 9. Flyway Migration Plan

All scripts live under `src/main/resources/db/migration/`.

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__extensions.sql` | Enable `pgcrypto` (`gen_random_uuid`) |
| V2 | `V2__iam_tables.sql` | `users`, `credentials`, `password_history`, `refresh_tokens`, `roles`, `permissions`, `role_permissions`, `user_roles` |
| V3 | `V3__content_tables.sql` | `document_categories`, `category_role_visibility`, `documents`, `document_acl` |
| V4 | `V4__personal_platform_tables.sql` | `user_settings`, `notifications`, `push_subscriptions` |
| V5 | `V5__audit_events_partitioned.sql` | `audit_events` parent + initial partitions (2026-05 → 2026-08) |
| V6 | `V6__indexes.sql` | All `CREATE INDEX CONCURRENTLY` statements |
| V7 | `V7__materialized_views.sql` | `mv_upload_trend`, `mv_storage_by_category` |
| V8 | `V8__seed_data.sql` | Built-in roles, permissions, role-permission matrix, demo users |

### V1 — Extensions

```sql
-- V1__extensions.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

### V6 — Note on CONCURRENTLY

Flyway runs migrations inside a transaction. `CREATE INDEX CONCURRENTLY` cannot run inside a transaction block. Mark these migrations with:

```java
// MigrationConfig.java
@Bean
FlywayConfigurationCustomizer flywayCustomizer() {
    return config -> config.baselineOnMigrate(true);
}
```

And split V6 into a `@NonTransactional` migration using the Java-based Flyway API (or split each index into its own repeatable migration `R__` file). Alternatively, use a Flyway 10 callback `beforeMigrate` to `SET LOCAL lock_timeout = '5s'`.

---

## 10. Seed Data

```sql
-- V8__seed_data.sql
-- ─────────────────────────────────────────────
--  Fixed UUIDs ensure idempotent re-seeding.
-- ─────────────────────────────────────────────

-- Roles
INSERT INTO roles (id, name, description, is_builtin) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Administrator', 'Full system access',        true),
    ('00000000-0000-0000-0000-000000000002', 'Manager',       'User and content management', true),
    ('00000000-0000-0000-0000-000000000003', 'Viewer',        'Read-only access',           true)
ON CONFLICT (id) DO NOTHING;

-- Permissions
INSERT INTO permissions (id, code, description, category) VALUES
    ('10000000-0000-0000-0000-000000000001', 'usersView',   'View user list',       'Users'),
    ('10000000-0000-0000-0000-000000000002', 'usersCreate', 'Create users',         'Users'),
    ('10000000-0000-0000-0000-000000000003', 'usersEdit',   'Edit users',           'Users'),
    ('10000000-0000-0000-0000-000000000004', 'usersDelete', 'Delete users',         'Users'),
    ('10000000-0000-0000-0000-000000000005', 'rolesView',   'View role list',       'Roles'),
    ('10000000-0000-0000-0000-000000000006', 'rolesEdit',   'Create / edit roles',  'Roles')
ON CONFLICT (id) DO NOTHING;

-- Role-permission matrix
-- Administrator: all 6 permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id
FROM   permissions
ON CONFLICT DO NOTHING;

-- Manager: usersView, usersCreate, usersEdit, rolesView
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id
FROM   permissions
WHERE  code IN ('usersView', 'usersCreate', 'usersEdit', 'rolesView')
ON CONFLICT DO NOTHING;

-- Viewer: no permissions (role row exists, zero permission rows)

-- ─────────────────────────────────────────────
--  Demo users  (passwords are bcrypt of the strings shown)
--  admin123   → $2a$12$... (pre-computed)
--  manager123 → $2a$12$...
--  viewer123  → $2a$12$...
-- ─────────────────────────────────────────────
INSERT INTO users (id, full_name, email, status) VALUES
    ('20000000-0000-0000-0000-000000000001', 'Admin User',    'admin@demo.com',   'active'),
    ('20000000-0000-0000-0000-000000000002', 'Manager User',  'manager@demo.com', 'active'),
    ('20000000-0000-0000-0000-000000000003', 'Viewer User',   'viewer@demo.com',  'active')
ON CONFLICT (id) DO NOTHING;

INSERT INTO credentials (user_id, password_hash) VALUES
    ('20000000-0000-0000-0000-000000000001',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj/oE.Jg.6Cy'), -- admin123
    ('20000000-0000-0000-0000-000000000002',
     '$2a$12$92ij7g3N1h4D5dM1VzpGH.pjZj7v6e/1LD2b0y3L0g0N8g4d.aVzK'), -- manager123
    ('20000000-0000-0000-0000-000000000003',
     '$2a$12$K3T2gVf3QL.iO2KfMpBP3ON2k1O3I8V5L9HsD1B0E4t.I7OxnIGPS')  -- viewer123
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id) VALUES
    ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003')
ON CONFLICT DO NOTHING;

-- Default user settings for demo accounts
INSERT INTO user_settings (user_id) VALUES
    ('20000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000003')
ON CONFLICT (user_id) DO NOTHING;

-- ─────────────────────────────────────────────
--  Sample document categories
-- ─────────────────────────────────────────────
INSERT INTO document_categories (id, name, description) VALUES
    ('30000000-0000-0000-0000-000000000001', 'Contracts',   'Legal contracts and agreements'),
    ('30000000-0000-0000-0000-000000000002', 'Reports',     'Monthly and quarterly reports'),
    ('30000000-0000-0000-0000-000000000003', 'HR Policies', 'Human resources policy documents')
ON CONFLICT (id) DO NOTHING;

-- All roles can view all sample categories; only Admin/Manager can upload/delete
INSERT INTO category_role_visibility (category_id, role_id, can_view, can_upload, can_delete)
SELECT c.id, r.id,
       true,                                            -- can_view
       r.name IN ('Administrator', 'Manager'),          -- can_upload
       r.name = 'Administrator'                         -- can_delete
FROM document_categories c
CROSS JOIN roles r
WHERE c.id IN (
    '30000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000003'
)
ON CONFLICT DO NOTHING;
```

---

## 11. Open Questions

| # | Question | Impact | Proposed Default |
|---|----------|--------|-----------------|
| 1 | Retention period for `audit_events` partitions? A monthly drop job (`DROP TABLE audit_events_YYYY_MM`) is simple but irreversible. | Compliance, storage | Keep 24 months; archive older partitions to S3 cold storage via `pg_dump` |
| 2 | Should `document_acl` rows be auto-created from `category_role_visibility` on category save, or lazily on first document access? | Implementation complexity | Eager creation via DB trigger on `category_role_visibility` INSERT/UPDATE |
| 3 | Password policy: enforce `password_history` check at DB level (trigger) or application level? | Defense-in-depth vs. coupling | Application level; DB trigger as backstop |
| 4 | Multi-role users: the current `user_roles` join table supports many-to-many, but the Angular UI assigns exactly one role. Resolve or embrace? | Feature scope | Leave join table in place; enforce single-role at application layer via constraint if needed |
| 5 | `documents.storage_key` format convention? `{category_id}/{year}/{month}/{uuid}/{filename}` keeps objects browsable in S3 console. | S3 lifecycle rules, presign URL construction | `{category_id}/{yyyy}/{MM}/{document_id}/{original_filename}` |
| 6 | Should `notifications` fanout be async (via a job queue) or synchronous? At 10k concurrent, synchronous fanout to 100 users per event could become a bottleneck. | Latency at scale | Async via an outbox pattern in `notifications` table + a scheduler |
| 7 | Partition creation automation: add next month's partition via Flyway on each deploy or via `pg_cron`? | Operational burden | `pg_cron` job running on the 25th of each month to create the following month's partition |

---

*Last updated: 2026-05-10*
