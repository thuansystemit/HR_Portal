-- ═══════════════════════════════════════════════════════════════
--  V14: Invoice record extraction table
--
--  One table that stores structured data extracted from INVOICE
--  documents by the cv-batch-extractor agent.
--
--  Complex nested data (vendor, buyer, lineItems) is stored as
--  JSONB to avoid a wide normalised schema for a first version.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE invoice_records (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id           UUID          NOT NULL,
    document_category_id  UUID          NOT NULL,
    invoice_number        VARCHAR(100),
    invoice_date          DATE,
    due_date              DATE,
    currency              VARCHAR(3),
    vendor                JSONB,
    buyer                 JSONB,
    line_items            JSONB,
    subtotal              NUMERIC(15,2),
    tax_amount            NUMERIC(15,2),
    total                 NUMERIC(15,2),
    notes                 TEXT,
    payment_terms         VARCHAR(300),
    confidence_overall    VARCHAR(6)    NOT NULL,
    low_confidence_fields JSONB,
    missing_fields        JSONB,
    extracted_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ir_document_fk FOREIGN KEY (document_id)
        REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT ir_document_uq UNIQUE (document_id),
    CONSTRAINT ir_document_category_fk FOREIGN KEY (document_category_id)
        REFERENCES document_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ir_confidence_overall_chk
        CHECK (confidence_overall IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_ir_document_category
    ON invoice_records (document_category_id);

CREATE INDEX idx_ir_invoice_number
    ON invoice_records (invoice_number)
    WHERE invoice_number IS NOT NULL;

CREATE INDEX idx_ir_invoice_date
    ON invoice_records (invoice_date)
    WHERE invoice_date IS NOT NULL;
