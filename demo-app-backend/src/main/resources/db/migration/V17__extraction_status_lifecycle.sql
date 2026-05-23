-- V17: Extraction status lifecycle
--
-- 1. Rename legacy COMPLETED → SUCCESS
-- 2. Add extraction timestamp columns
-- 3. Lock down valid status values with a CHECK constraint
-- 4. Backfill timestamps for existing SUCCESS rows
-- 5. Add indexes for polling and category-scoped queries

UPDATE documents
    SET extraction_status = 'SUCCESS'
    WHERE extraction_status = 'COMPLETED';

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS extraction_started_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS extraction_finished_at TIMESTAMPTZ;

ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS doc_extraction_status_chk;

ALTER TABLE documents
    ADD CONSTRAINT doc_extraction_status_chk
        CHECK (extraction_status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'));

-- Backfill timestamps for rows that were already successfully extracted
UPDATE documents
    SET extraction_started_at  = uploaded_at,
        extraction_finished_at = uploaded_at
    WHERE extraction_status = 'SUCCESS';

CREATE INDEX IF NOT EXISTS idx_doc_extraction_status
    ON documents (extraction_status)
    WHERE extraction_status IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_doc_cat_extraction_status
    ON documents (category_id, extraction_status)
    WHERE extraction_status IS NOT NULL AND deleted_at IS NULL;
