-- V20: Candidate hiring status — denormalised column that tracks the highest-priority
-- recruitment stage across all job applications for a candidate.

ALTER TABLE cv_candidates
    ADD COLUMN hiring_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
        CONSTRAINT cv_cand_hiring_status_chk
            CHECK (hiring_status IN ('AVAILABLE','IN_PROCESS','OFFERED','HIRED','REJECTED','WITHDRAWN'));

-- Backfill from existing job_applications (priority: HIRED > OFFERED > IN_PROCESS > REJECTED)
UPDATE cv_candidates c
SET hiring_status =
    CASE
        WHEN EXISTS (
            SELECT 1 FROM job_applications ja
            WHERE ja.cv_candidate_id = c.id AND ja.stage = 'HIRED')              THEN 'HIRED'
        WHEN EXISTS (
            SELECT 1 FROM job_applications ja
            WHERE ja.cv_candidate_id = c.id AND ja.stage = 'OFFER')              THEN 'OFFERED'
        WHEN EXISTS (
            SELECT 1 FROM job_applications ja
            WHERE ja.cv_candidate_id = c.id
              AND ja.stage IN ('INTERVIEW','SCREENING','APPLIED'))                THEN 'IN_PROCESS'
        WHEN EXISTS (
            SELECT 1 FROM job_applications ja
            WHERE ja.cv_candidate_id = c.id AND ja.stage = 'REJECTED')           THEN 'REJECTED'
        ELSE 'AVAILABLE'
    END;

CREATE INDEX idx_cv_candidates_hiring_status ON cv_candidates(hiring_status);
