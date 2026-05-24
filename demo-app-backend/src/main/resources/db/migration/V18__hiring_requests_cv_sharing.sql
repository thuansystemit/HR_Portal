-- ============================================================================
-- V18: Hiring Requests, CV Sharing, and Job Posting Skills
--
-- Three features for Sprint 2:
--   1. hiring_requests        — Dev Team submits formal hiring requests (G14)
--   2. cv_shares              — HR shares candidate CVs with Dev Team (G15, G16)
--   3. job_posting_skills     — Structured skills on job postings (G3)
--
-- Dependencies: users (V2), job_postings (V17), cv_candidates (V10)
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. HIRING REQUESTS
--    Dev Team members submit requests specifying role type, urgency, and
--    description. HR processes these and optionally links them to a job posting.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE hiring_requests (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id      UUID         NOT NULL REFERENCES users(id),
    title             VARCHAR(300) NOT NULL,
    description       TEXT,
    role_type         VARCHAR(20)  NOT NULL,
    department        VARCHAR(200) NOT NULL,
    urgency           VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    job_posting_id    UUID         REFERENCES job_postings(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT hr_role_type_chk CHECK (role_type IN ('FRONTEND', 'BACKEND', 'FULLSTACK')),
    CONSTRAINT hr_urgency_chk   CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT hr_status_chk    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'CANDIDATE_FOUND', 'HIRED', 'CLOSED', 'REJECTED'))
);

COMMENT ON TABLE hiring_requests IS
    'Formal hiring requests submitted by Dev Team members. Lifecycle: PENDING -> IN_PROGRESS -> CANDIDATE_FOUND -> HIRED -> CLOSED (or REJECTED at any point). Linked to job_postings once HR creates a posting in response.';

-- Indexes for hiring_requests
CREATE INDEX idx_hiring_requests_requester  ON hiring_requests(requester_id);
CREATE INDEX idx_hiring_requests_status     ON hiring_requests(status);
CREATE INDEX idx_hiring_requests_job_posting ON hiring_requests(job_posting_id);


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. CV SHARES
--    HR shares a shortlisted candidate's CV with the Dev Team member who
--    submitted the hiring request. The Dev Team member records a preliminary
--    impression (INTERESTED / NOT_INTERESTED / REVIEW_LATER) with an optional
--    comment.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE cv_shares (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    hiring_request_id  UUID         NOT NULL REFERENCES hiring_requests(id) ON DELETE CASCADE,
    cv_candidate_id    UUID         NOT NULL REFERENCES cv_candidates(id) ON DELETE CASCADE,
    shared_by          UUID         NOT NULL REFERENCES users(id),
    shared_with        UUID         NOT NULL REFERENCES users(id),
    impression         VARCHAR(20),
    comment            TEXT,
    shared_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at        TIMESTAMPTZ,

    CONSTRAINT cs_impression_chk CHECK (
        impression IS NULL OR impression IN ('INTERESTED', 'NOT_INTERESTED', 'REVIEW_LATER')
    ),
    CONSTRAINT cs_unique UNIQUE (hiring_request_id, cv_candidate_id, shared_with)
);

COMMENT ON TABLE cv_shares IS
    'Tracks HR sharing candidate CVs with Dev Team members. Each row represents one candidate shared for one hiring request to one reviewer. The reviewer records their preliminary impression before HR proceeds to formal application and interview scheduling.';

-- Indexes for cv_shares
CREATE INDEX idx_cv_shares_hiring_request ON cv_shares(hiring_request_id);
CREATE INDEX idx_cv_shares_candidate      ON cv_shares(cv_candidate_id);
CREATE INDEX idx_cv_shares_shared_with    ON cv_shares(shared_with);
CREATE INDEX idx_cv_shares_shared_by      ON cv_shares(shared_by);
CREATE INDEX idx_cv_shares_impression     ON cv_shares(impression) WHERE impression IS NOT NULL;


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. JOB POSTING SKILLS
--    Structured skills linked to job postings (many-to-many). Each skill has
--    a name and a flag indicating whether it is required or merely preferred.
--    Enables job-to-candidate matching and skill-demand analytics.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE job_posting_skills (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    job_posting_id  UUID          NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    skill_name      VARCHAR(100)  NOT NULL,
    is_required     BOOLEAN       NOT NULL DEFAULT true,

    CONSTRAINT jps_unique UNIQUE (job_posting_id, skill_name)
);

COMMENT ON TABLE job_posting_skills IS
    'Structured skills associated with a job posting. Each skill is marked as required (must-have) or preferred (nice-to-have). Used for candidate matching and skill-demand analytics.';

-- Indexes for job_posting_skills
CREATE INDEX idx_job_posting_skills_posting ON job_posting_skills(job_posting_id);
CREATE INDEX idx_job_posting_skills_name    ON job_posting_skills(skill_name);
