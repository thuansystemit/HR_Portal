-- V21: Add optional interviewer_id to interviews table for G6 (interview assignment notification)
-- Rollback: ALTER TABLE interviews DROP COLUMN interviewer_id;

ALTER TABLE interviews
    ADD COLUMN IF NOT EXISTS interviewer_id UUID REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_interviews_interviewer ON interviews(interviewer_id)
    WHERE interviewer_id IS NOT NULL;
