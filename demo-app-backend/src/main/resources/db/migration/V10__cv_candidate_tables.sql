-- ═══════════════════════════════════════════════════════════════
--  V10: CV candidate extraction tables
--
--  Six tables that store structured data extracted from CV
--  documents by the document-extractor agent.
--
--  Parent: cv_candidates (one row per extracted document)
--  Children: cv_work_experiences, cv_educations,
--            cv_technical_skills, cv_languages,
--            cv_certifications
--
--  All child tables cascade-delete when the parent is removed.
-- ═══════════════════════════════════════════════════════════════

-- ───────────────────────────────────────────────────────────────
-- 1. cv_candidates
-- ───────────────────────────────────────────────────────────────
CREATE TABLE cv_candidates (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id           UUID         NOT NULL,
    document_category_id  UUID         NOT NULL,
    full_name             VARCHAR(300) NOT NULL,
    email                 VARCHAR(320),
    phone                 VARCHAR(20),
    city                  VARCHAR(200),
    country               CHAR(2),
    linkedin_url          VARCHAR(500),
    github_url            VARCHAR(500),
    portfolio_url         VARCHAR(500),
    summary               TEXT,
    tools_and_frameworks  JSONB,
    soft_skills           JSONB,
    projects              JSONB,
    publications          JSONB,
    confidence_overall    VARCHAR(6)   NOT NULL,
    low_confidence_fields JSONB,
    missing_fields        JSONB,
    raw_language          CHAR(2),
    extracted_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT cvc_document_fk FOREIGN KEY (document_id)
        REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT cvc_document_uq UNIQUE (document_id),
    CONSTRAINT cvc_document_category_fk FOREIGN KEY (document_category_id)
        REFERENCES document_categories (id) ON DELETE RESTRICT,
    CONSTRAINT cvc_confidence_overall_chk
        CHECK (confidence_overall IN ('HIGH', 'MEDIUM', 'LOW'))
);

-- ───────────────────────────────────────────────────────────────
-- 2. cv_work_experiences
-- ───────────────────────────────────────────────────────────────
CREATE TABLE cv_work_experiences (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cv_candidate_id   UUID         NOT NULL,
    sort_order        SMALLINT     NOT NULL,
    company           VARCHAR(300) NOT NULL,
    title             VARCHAR(300) NOT NULL,
    start_date        DATE,
    start_date_precision VARCHAR(5),
    end_date          DATE,
    is_current        BOOLEAN      NOT NULL DEFAULT false,
    location          VARCHAR(300),
    is_remote         BOOLEAN,
    responsibilities  JSONB,
    achievements      JSONB,
    technologies      JSONB,

    CONSTRAINT cvw_cv_candidate_fk FOREIGN KEY (cv_candidate_id)
        REFERENCES cv_candidates (id) ON DELETE CASCADE,
    CONSTRAINT cvw_start_date_precision_chk
        CHECK (start_date_precision IN ('YEAR', 'MONTH'))
);

-- ───────────────────────────────────────────────────────────────
-- 3. cv_educations
-- ───────────────────────────────────────────────────────────────
CREATE TABLE cv_educations (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cv_candidate_id UUID        NOT NULL,
    sort_order     SMALLINT     NOT NULL,
    institution    VARCHAR(400) NOT NULL,
    degree         VARCHAR(200) NOT NULL,
    field_of_study VARCHAR(300),
    start_year     SMALLINT,
    end_year       SMALLINT,
    gpa            NUMERIC(4,2),
    honors         VARCHAR(300),

    CONSTRAINT cve_cv_candidate_fk FOREIGN KEY (cv_candidate_id)
        REFERENCES cv_candidates (id) ON DELETE CASCADE
);

-- ───────────────────────────────────────────────────────────────
-- 4. cv_technical_skills
-- ───────────────────────────────────────────────────────────────
CREATE TABLE cv_technical_skills (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cv_candidate_id  UUID         NOT NULL,
    skill_name       VARCHAR(200) NOT NULL,
    skill_name_lower VARCHAR(200) NOT NULL
                         GENERATED ALWAYS AS (lower(skill_name)) STORED,

    CONSTRAINT cvts_cv_candidate_fk FOREIGN KEY (cv_candidate_id)
        REFERENCES cv_candidates (id) ON DELETE CASCADE,
    CONSTRAINT cvts_candidate_skill_uq UNIQUE (cv_candidate_id, skill_name_lower)
);

-- ───────────────────────────────────────────────────────────────
-- 5. cv_languages
-- ───────────────────────────────────────────────────────────────
CREATE TABLE cv_languages (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cv_candidate_id UUID         NOT NULL,
    language        VARCHAR(100) NOT NULL,
    proficiency     VARCHAR(20),

    CONSTRAINT cvl_cv_candidate_fk FOREIGN KEY (cv_candidate_id)
        REFERENCES cv_candidates (id) ON DELETE CASCADE,
    CONSTRAINT cvl_proficiency_chk
        CHECK (proficiency IN ('Native', 'Fluent', 'Professional', 'Conversational', 'Basic'))
);

-- ───────────────────────────────────────────────────────────────
-- 6. cv_certifications
-- ───────────────────────────────────────────────────────────────
CREATE TABLE cv_certifications (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cv_candidate_id UUID         NOT NULL,
    name            VARCHAR(400) NOT NULL,
    issuer          VARCHAR(300),
    issued_date     DATE,
    expiry_date     DATE,
    credential_id   VARCHAR(200),

    CONSTRAINT cvce_cv_candidate_fk FOREIGN KEY (cv_candidate_id)
        REFERENCES cv_candidates (id) ON DELETE CASCADE
);


-- ═══════════════════════════════════════════════════════════════
--  INDEXES
-- ═══════════════════════════════════════════════════════════════

-- cv_candidates
CREATE INDEX idx_cvc_document_category
    ON cv_candidates (document_category_id);

CREATE INDEX idx_cvc_country
    ON cv_candidates (country);

CREATE INDEX idx_cvc_email
    ON cv_candidates (email);

-- cv_work_experiences
CREATE INDEX idx_cvw_candidate_sort
    ON cv_work_experiences (cv_candidate_id, sort_order);

CREATE INDEX idx_cvw_company
    ON cv_work_experiences (company);

CREATE INDEX idx_cvw_title
    ON cv_work_experiences (title);

-- cv_educations
CREATE INDEX idx_cve_candidate_sort
    ON cv_educations (cv_candidate_id, sort_order);

CREATE INDEX idx_cve_institution
    ON cv_educations (institution);

-- cv_technical_skills
CREATE INDEX idx_cvts_skill_name_lower
    ON cv_technical_skills (skill_name_lower);

CREATE INDEX idx_cvts_cv_candidate
    ON cv_technical_skills (cv_candidate_id);

-- cv_languages
CREATE INDEX idx_cvl_language_proficiency
    ON cv_languages (language, proficiency);

-- cv_certifications
CREATE INDEX idx_cvce_name
    ON cv_certifications (name);
