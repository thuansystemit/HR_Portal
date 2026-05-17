ALTER TABLE document_categories
    ADD COLUMN llm_extraction BOOLEAN NOT NULL DEFAULT TRUE;
