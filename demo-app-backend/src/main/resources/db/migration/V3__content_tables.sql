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

CREATE TABLE documents (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id   UUID         NOT NULL,
    name          VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(127) NOT NULL DEFAULT 'application/octet-stream',
    size_bytes    BIGINT       NOT NULL DEFAULT 0 CHECK (size_bytes >= 0),
    storage_key   VARCHAR(512) NOT NULL,
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
