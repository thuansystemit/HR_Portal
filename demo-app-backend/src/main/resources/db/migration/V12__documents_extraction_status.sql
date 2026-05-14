ALTER TABLE documents
    ADD COLUMN extraction_status VARCHAR(20),
    ADD COLUMN extraction_error  TEXT;
