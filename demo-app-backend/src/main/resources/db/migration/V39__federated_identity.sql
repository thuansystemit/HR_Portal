-- IA-2(12) / IA-8(2): Federated identity table for SAML2 SP JIT provisioning.
--
-- Links an external IdP identity (provider + NameID) to a local User account.
-- One row per unique (provider, name_id) pair; the unique constraint enforces
-- that a PIV/CAC DN or login.gov subject cannot be claimed by two local users.
--
-- attributes JSONB stores raw SAML assertion attributes (e.g. email, given_name,
-- family_name, dn) for audit purposes and to support re-provisioning without
-- a new SAML round-trip.

CREATE TABLE federated_identities (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL,
    provider       VARCHAR(100) NOT NULL,
    name_id        VARCHAR(512) NOT NULL,
    name_id_format VARCHAR(200),
    attributes     JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_federated_identities PRIMARY KEY (id),
    CONSTRAINT fk_federated_identities_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_federated_identities_provider_nameid UNIQUE (provider, name_id)
);

CREATE INDEX idx_federated_identities_user_id ON federated_identities(user_id);
