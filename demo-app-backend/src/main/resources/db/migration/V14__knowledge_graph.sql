-- ═══════════════════════════════════════════════════════════════
--  V14: TECHNICAL document type + Knowledge Graph tables
--
--  Phase 1 of the Knowledge Graph feature: text-based search
--  (no vector embeddings yet — those come in V15).
--
--  This migration:
--    1. Extends the document_type CHECK constraint to allow
--       'TECHNICAL' documents alongside CV and INVOICE.
--    2. Installs the pg_trgm extension for trigram fuzzy search.
--    3. Creates three knowledge graph tables:
--       - knowledge_entities:          nodes in the graph
--       - knowledge_relationships:     directed edges
--       - knowledge_entity_sources:    citation/provenance links
--
--  Design decisions:
--    - Entity types are VARCHAR + CHECK (not ENUM) so new types
--      can be added with a simple constraint replacement.
--    - Relationship types are unconstrained VARCHAR(100) to allow
--      the LLM extractor to discover new relation types without
--      schema changes. Known types are documented in comments.
--    - The aliases column uses TEXT[] (Postgres array) rather
--      than a separate table because aliases are always fetched
--      with the entity and the set is small (< 10 per entity).
--    - properties JSONB stores flexible attributes that vary by
--      entity_type (e.g., a Technology entity may have "version",
--      "license", "homepage"; a Person may have "title", "email").
-- ═══════════════════════════════════════════════════════════════


-- ───────────────────────────────────────────────────────────────
-- 1. Extend document_type CHECK to include TECHNICAL
--
--    Strategy: DROP the old named constraint, ADD a new one with
--    all three allowed values. Widening a CHECK constraint is a
--    metadata-only operation — no row rewrite.
-- ───────────────────────────────────────────────────────────────
ALTER TABLE document_categories
    DROP CONSTRAINT dc_document_type_chk;

ALTER TABLE document_categories
    ADD CONSTRAINT dc_document_type_chk
        CHECK (document_type IN ('CV', 'INVOICE', 'TECHNICAL'));


-- ───────────────────────────────────────────────────────────────
-- 2. Install pg_trgm extension
--
--    Required for GIN trigram indexes that power fuzzy / ILIKE
--    searches on entity names. Safe to install multiple times.
-- ───────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pg_trgm;


-- ───────────────────────────────────────────────────────────────
-- 3. knowledge_entities
--
--    Each row represents a single node in the knowledge graph:
--    a named concept extracted from one or more TECHNICAL
--    documents by the LLM pipeline.
--
--    entity_type constrains the node to one of five categories:
--      - Technology:  programming languages, frameworks, tools
--      - Concept:     design patterns, algorithms, methodologies
--      - Person:      authors, contributors, domain experts
--      - Project:     codebases, products, initiatives
--      - Team:        organizational units, squads
--
--    aliases TEXT[]:
--      Alternative names / spellings / abbreviations.
--      Example: entity name "PostgreSQL" might have aliases
--      ['Postgres', 'PG', 'psql']. Stored as a Postgres array
--      for compact storage and efficient GIN @> lookups.
--
--    properties JSONB:
--      Flexible key-value attributes that vary by entity_type.
--      GIN-indexed for ad-hoc queries (e.g., find all
--      technologies where properties->>'license' = 'MIT').
-- ───────────────────────────────────────────────────────────────
CREATE TABLE knowledge_entities (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID         NOT NULL,
    entity_type  VARCHAR(50)  NOT NULL
                     CHECK (entity_type IN (
                         'Technology', 'Concept', 'Person',
                         'Project', 'Team'
                     )),
    name         VARCHAR(255) NOT NULL,
    aliases      TEXT[],
    properties   JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ke_document_fk FOREIGN KEY (document_id)
        REFERENCES documents (id) ON DELETE CASCADE
);

-- GIN index on aliases: WHERE aliases @> ARRAY['PG']::TEXT[]
CREATE INDEX idx_ke_aliases
    ON knowledge_entities USING gin (aliases);

-- Trigram GIN index on name for fuzzy / ILIKE search.
-- Supports: WHERE name ILIKE '%postg%' or similarity() > 0.3
CREATE INDEX idx_ke_name_trgm
    ON knowledge_entities USING gin (name gin_trgm_ops);

-- Filter by entity type + sorted listing.
CREATE INDEX idx_ke_entity_type
    ON knowledge_entities (entity_type);

-- FK index: all entities extracted from a given document.
CREATE INDEX idx_ke_document_id
    ON knowledge_entities (document_id);

-- GIN index on properties: WHERE properties @> '{"license":"MIT"}'
CREATE INDEX idx_ke_properties
    ON knowledge_entities USING gin (properties jsonb_path_ops);


-- ───────────────────────────────────────────────────────────────
-- 4. knowledge_relationships
--
--    A directed edge between two knowledge_entities.
--    The (source, target, relation_type) triple is unique —
--    no duplicate edges of the same type between any pair.
--
--    relation_type is unconstrained VARCHAR(100) to allow the
--    LLM to discover new relationship types. Known types:
--      USES, DEPENDS_ON, IMPLEMENTS, EXTENDS, PART_OF,
--      CREATED_BY, DOCUMENTED_IN, PREREQUISITE_OF,
--      RELATED_TO, SUPERSEDES, COMPATIBLE_WITH
--
--    weight FLOAT (default 1.0):
--      Confidence / strength of the relationship, range 0.0–1.0.
--      Assigned by the LLM based on how explicitly the
--      relationship appears in the source document.
-- ───────────────────────────────────────────────────────────────
CREATE TABLE knowledge_relationships (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    source_entity_id  UUID          NOT NULL,
    target_entity_id  UUID          NOT NULL,
    relation_type     VARCHAR(100)  NOT NULL,
    weight            FLOAT         NOT NULL DEFAULT 1.0,
    properties        JSONB,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT kr_source_fk FOREIGN KEY (source_entity_id)
        REFERENCES knowledge_entities (id) ON DELETE CASCADE,
    CONSTRAINT kr_target_fk FOREIGN KEY (target_entity_id)
        REFERENCES knowledge_entities (id) ON DELETE CASCADE,

    CONSTRAINT kr_source_target_type_uq
        UNIQUE (source_entity_id, target_entity_id, relation_type),

    CONSTRAINT kr_no_self_loop_chk
        CHECK (source_entity_id <> target_entity_id)
);

-- Outgoing edges from a node, optionally filtered by type.
CREATE INDEX idx_kr_source_entity
    ON knowledge_relationships (source_entity_id, relation_type);

-- Incoming edges to a node: "what depends on entity Y?"
CREATE INDEX idx_kr_target_entity
    ON knowledge_relationships (target_entity_id, relation_type);


-- ───────────────────────────────────────────────────────────────
-- 5. knowledge_entity_sources
--
--    Provenance / citation table: links an entity back to the
--    specific document and excerpt where it was found.
--
--    Composite PK (entity_id, document_id) is the natural key
--    and enforces uniqueness without an extra constraint. One
--    representative excerpt per (entity, document) pair is kept;
--    multiple mentions in one document do not create extra rows.
-- ───────────────────────────────────────────────────────────────
CREATE TABLE knowledge_entity_sources (
    entity_id    UUID        NOT NULL,
    document_id  UUID        NOT NULL,
    excerpt      TEXT,
    page_number  INT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (entity_id, document_id),

    CONSTRAINT kes_entity_fk FOREIGN KEY (entity_id)
        REFERENCES knowledge_entities (id) ON DELETE CASCADE,
    CONSTRAINT kes_document_fk FOREIGN KEY (document_id)
        REFERENCES documents (id) ON DELETE CASCADE
);

-- Reverse lookup: all entities found in a given document.
CREATE INDEX idx_kes_document_id
    ON knowledge_entity_sources (document_id);
