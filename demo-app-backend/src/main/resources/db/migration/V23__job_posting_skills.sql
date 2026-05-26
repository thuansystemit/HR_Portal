CREATE TABLE IF NOT EXISTS job_posting_skills (
    id             UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    job_posting_id UUID         NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    skill_name     VARCHAR(100) NOT NULL,
    is_required    BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (job_posting_id, skill_name)
);

CREATE INDEX IF NOT EXISTS idx_job_posting_skills_posting ON job_posting_skills (job_posting_id);
CREATE INDEX IF NOT EXISTS idx_job_posting_skills_name    ON job_posting_skills (skill_name);
