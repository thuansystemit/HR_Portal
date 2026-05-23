-- Case 1: NULL status — uploaded before the extraction_status feature; set to PENDING so they can be retried.
UPDATE documents
SET extraction_status = 'PENDING'
WHERE extraction_status IS NULL
  AND deleted_at IS NULL;

-- Case 2: PENDING + already has a cv_candidate or invoice_record — extraction succeeded but the status
--         transition was blocked by the old canTransitionTo() guard; mark as SUCCESS.
UPDATE documents
SET    extraction_status      = 'SUCCESS',
       extraction_finished_at = COALESCE(extraction_finished_at, NOW())
WHERE  extraction_status = 'PENDING'
  AND  deleted_at IS NULL
  AND  (
         EXISTS (SELECT 1 FROM cv_candidates    WHERE document_id = documents.id)
      OR EXISTS (SELECT 1 FROM invoice_records  WHERE document_id = documents.id)
  );

-- Case 3: PENDING + no extracted record — extraction ran during the buggy period and produced no result;
--         mark as FAILED so the Retry button appears in the UI.
UPDATE documents
SET    extraction_status = 'FAILED',
       extraction_error  = 'Document was not processed successfully. Use the retry button to re-run extraction.'
WHERE  extraction_status = 'PENDING'
  AND  deleted_at IS NULL
  AND  NOT EXISTS (SELECT 1 FROM cv_candidates   WHERE document_id = documents.id)
  AND  NOT EXISTS (SELECT 1 FROM invoice_records  WHERE document_id = documents.id);
