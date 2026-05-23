-- V15: Widen CV columns that are too narrow for real-world LLM output
--
-- cv_languages.proficiency: VARCHAR(20) → VARCHAR(100)
--   LLM returns free-form values like "Business Level (JLPT N3)" (24 chars)
--
-- cv_candidates.phone: VARCHAR(20) → VARCHAR(30)
--   Future-proofing for formatted phone numbers with extensions

ALTER TABLE cv_languages  ALTER COLUMN proficiency TYPE VARCHAR(100);
ALTER TABLE cv_candidates ALTER COLUMN phone        TYPE VARCHAR(30);
