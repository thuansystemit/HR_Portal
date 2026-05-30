-- SI-12: track when a CV candidate record was anonymised for data-retention enforcement
ALTER TABLE cv_candidates ADD COLUMN anonymized_at TIMESTAMPTZ;
