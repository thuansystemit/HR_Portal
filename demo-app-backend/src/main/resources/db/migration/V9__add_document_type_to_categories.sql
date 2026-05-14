-- ═══════════════════════════════════════════════════════════════
--  V9: Add document_type to document_categories
--
--  Business rule: every category must declare the type of
--  documents it holds (CV or INVOICE). No untyped categories.
--
--  Strategy:
--    1. ADD COLUMN with NOT NULL DEFAULT — Postgres 11+ rewrites
--       only the catalogue, not every row (zero-downtime safe).
--    2. Backfill the 3 existing seed rows to accurate values.
--    3. Add CHECK constraint for allowed values.
--    4. Add a regular index filtered to non-deleted rows to
--       support "list all categories of type X" queries.
-- ═══════════════════════════════════════════════════════════════

-- Step 1: Add column with NOT NULL and a safe default.
-- The default 'INVOICE' is chosen because it is the most common
-- type among the existing seed rows (Contracts, Reports are both
-- INVOICE-related; only HR Policies is CV-related).
ALTER TABLE document_categories
    ADD COLUMN document_type VARCHAR(20) NOT NULL DEFAULT 'INVOICE';

-- Step 2: Add CHECK constraint for allowed values.
-- Using VARCHAR + CHECK rather than a Postgres ENUM so that new
-- document types can be added with a simple ALTER TABLE … CHECK
-- replacement, without the CREATE TYPE / ALTER TYPE dance.
ALTER TABLE document_categories
    ADD CONSTRAINT dc_document_type_chk
        CHECK (document_type IN ('CV', 'INVOICE'));

-- Step 3: Backfill existing seed rows to accurate values.
-- Contracts  → INVOICE (legal agreements are invoice-adjacent business docs)
-- Reports    → INVOICE (financial/operational reports)
-- HR Policies → CV      (human resources / personnel documents)
UPDATE document_categories
   SET document_type = 'CV',
       updated_at    = now()
 WHERE id = '30000000-0000-0000-0000-000000000003';   -- HR Policies

-- Contracts and Reports already have the correct default (INVOICE),
-- but we touch updated_at to leave an explicit audit trail.
UPDATE document_categories
   SET updated_at = now()
 WHERE id IN (
    '30000000-0000-0000-0000-000000000001',            -- Contracts
    '30000000-0000-0000-0000-000000000002'             -- Reports
 );

-- Step 4: Add index for "list categories by document_type" queries.
-- A regular B-tree index filtered to non-deleted rows is appropriate
-- here. With only two distinct values the selectivity is ~50%, which
-- is borderline for an index. However:
--   (a) the table is small (hundreds of rows at most), so the index
--       is tiny and maintenance cost is negligible;
--   (b) the query "show me all CV categories" is a primary UI filter
--       that benefits from an index-only scan on (document_type, name);
--   (c) a composite index on (document_type, name) supports both
--       the filter and the ORDER BY in a single scan.
CREATE INDEX idx_dc_document_type
    ON document_categories (document_type, name)
    WHERE deleted_at IS NULL;
