CREATE TABLE users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(254) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'inactive')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT users_email_uq UNIQUE (email)
);

CREATE TABLE credentials (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    password_hash   VARCHAR(72) NOT NULL,
    failed_attempts INT         NOT NULL DEFAULT 0
                        CHECK (failed_attempts >= 0),
    locked_until    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT credentials_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT credentials_user_uq UNIQUE (user_id)
);

CREATE TABLE password_history (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL,
    password_hash VARCHAR(72) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ph_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(512),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT rt_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT rt_token_hash_uq UNIQUE (token_hash)
);

CREATE TABLE roles (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    is_builtin  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT roles_name_uq UNIQUE (name)
);

CREATE TABLE permissions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(60) NOT NULL,
    description VARCHAR(255),
    category    VARCHAR(40) NOT NULL DEFAULT 'general',

    CONSTRAINT permissions_code_uq UNIQUE (code)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,

    PRIMARY KEY (role_id, permission_id),

    CONSTRAINT rp_role_fk FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT rp_permission_fk FOREIGN KEY (permission_id)
        REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE TABLE user_roles (
    user_id    UUID        NOT NULL,
    role_id    UUID        NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT ur_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ur_role_fk FOREIGN KEY (role_id)
        REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT ur_granted_by_fk FOREIGN KEY (granted_by)
        REFERENCES users (id) ON DELETE SET NULL
);
