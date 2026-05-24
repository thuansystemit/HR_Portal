-- V17: Recruitment Pipeline tables

CREATE TABLE job_postings (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(300) NOT NULL,
    department   VARCHAR(200),
    location     VARCHAR(200),
    description  TEXT,
    requirements TEXT,
    deadline     DATE,
    status       VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    created_by   UUID         NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT jp_status_chk CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED'))
);

CREATE TABLE job_applications (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_posting_id   UUID        NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    cv_candidate_id  UUID        NOT NULL REFERENCES cv_candidates(id) ON DELETE CASCADE,
    stage            VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    notes            TEXT,
    applied_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ja_unique   UNIQUE (job_posting_id, cv_candidate_id),
    CONSTRAINT ja_stage_chk CHECK (stage IN ('APPLIED','SCREENING','INTERVIEW','OFFER','HIRED','REJECTED'))
);

CREATE TABLE application_stage_history (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    from_stage     VARCHAR(20),
    to_stage       VARCHAR(20) NOT NULL,
    moved_by       UUID        NOT NULL REFERENCES users(id),
    notes          TEXT,
    moved_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE interviews (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID         NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    scheduled_at   TIMESTAMPTZ  NOT NULL,
    meeting_link   VARCHAR(500),
    notes          TEXT,
    created_by     UUID         NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE interview_feedback (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id   UUID        NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    reviewer_id    UUID        NOT NULL REFERENCES users(id),
    rating         SMALLINT    NOT NULL,
    notes          TEXT,
    recommendation VARCHAR(10) NOT NULL,
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT if_unique   UNIQUE (interview_id, reviewer_id),
    CONSTRAINT if_rating_chk CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT if_rec_chk    CHECK (recommendation IN ('PASS','HOLD','REJECT'))
);

CREATE INDEX idx_job_applications_posting  ON job_applications(job_posting_id);
CREATE INDEX idx_job_applications_stage    ON job_applications(stage);
CREATE INDEX idx_stage_history_application ON application_stage_history(application_id);
CREATE INDEX idx_interviews_application    ON interviews(application_id);
CREATE INDEX idx_feedback_interview        ON interview_feedback(interview_id);
