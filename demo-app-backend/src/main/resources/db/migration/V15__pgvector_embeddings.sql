-- ═══════════════════════════════════════════════════════════════
--  V15: pgvector extension + document chunk embeddings
--
--  Phase 2 of the Knowledge Graph feature: vector search for
--  Retrieval-Augmented Generation (RAG).
--
--  This migration:
--    1. Installs the pgvector extension.
--    2. Creates the document_chunk_embeddings table that stores
--       chunked document text alongside its vector embedding.
--
--  Design decisions:
--    - vector(1536) matches OpenAI text-embedding-3-small.
--      Switching to a larger model (e.g., text-embedding-3-large
--      at 3072 dims) requires a new migration to ALTER the column.
--    - HNSW is chosen over IVFFlat because:
--        (a) no training step (no need for a representative sample),
--        (b) better recall at similar latency,
--        (c) incrementally updatable without full rebuild.
--    - Cosine distance (vector_cosine_ops) is the standard choice
--      for OpenAI embeddings (normalized vectors).
--    - Chunking strategy is handled by the application layer
--      (typically 512 tokens, 50-token overlap). The DB stores
--      the result, not the chunking logic.
-- ═══════════════════════════════════════════════════════════════


-- ───────────────────────────────────────────────────────────────
-- 1. Install pgvector extension
--
--    In managed environments (AWS RDS, GCP CloudSQL) the
--    extension is pre-installed; CREATE EXTENSION just activates
--    it for this database. Requires CREATE privilege.
-- ───────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS vector;


-- ───────────────────────────────────────────────────────────────
-- 2. document_chunk_embeddings
--
--    Each row stores one chunk of a document along with its
--    1536-dimensional vector embedding.
--
--    embedding vector(1536):
--      Nullable — allows inserting chunks before the embedding
--      API call completes. The application backfills embeddings
--      asynchronously after the chunk is persisted.
--
--    (document_id, chunk_index) unique constraint:
--      Re-embedding a document deletes existing chunks and
--      inserts fresh ones, preventing phantom duplicates.
-- ───────────────────────────────────────────────────────────────
CREATE TABLE document_chunk_embeddings (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID         NOT NULL,
    chunk_index   INT          NOT NULL,
    chunk_text    TEXT         NOT NULL,
    embedding     vector(1536),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT dce_document_fk FOREIGN KEY (document_id)
        REFERENCES documents (id) ON DELETE CASCADE,

    CONSTRAINT dce_document_chunk_uq
        UNIQUE (document_id, chunk_index)
);

-- Retrieve ordered chunks for a given document.
-- Supports: WHERE document_id = ? ORDER BY chunk_index
CREATE INDEX idx_dce_document_chunk
    ON document_chunk_embeddings (document_id, chunk_index);

-- HNSW index for approximate nearest-neighbor vector search.
--   m = 16:               bi-directional links per node
--                          (higher = better recall, more memory)
--   ef_construction = 64:  search width during index build
--                          (higher = better recall, slower build)
--
-- These defaults provide ~98% recall and good query latency
-- for tables up to ~1M rows. At query time, raise hnsw.ef_search
-- above the default of 40 for even better recall when needed:
--   SET LOCAL hnsw.ef_search = 100;
CREATE INDEX idx_dce_embedding_hnsw
    ON document_chunk_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
