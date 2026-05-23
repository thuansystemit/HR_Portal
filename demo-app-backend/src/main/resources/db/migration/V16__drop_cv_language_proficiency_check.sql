-- V16: Drop rigid proficiency enum constraint from cv_languages
--
-- The LLM returns free-form proficiency values (e.g. "Business Level (JLPT N3)")
-- that do not match the original fixed enum. Column is already VARCHAR(100)
-- from V15; this migration removes the CHECK constraint that blocked inserts.

ALTER TABLE cv_languages DROP CONSTRAINT IF EXISTS cvl_proficiency_chk;
