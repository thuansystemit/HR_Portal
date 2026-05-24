-- ═══════════════════════════════════════════════════════════════
--  V16: Indexes for CV candidate search & ranking (Story CS-1)
--
--  These indexes support ILIKE partial matching on the search
--  endpoint GET /api/v1/cv-candidates/search.
-- ═══════════════════════════════════════════════════════════════

-- Location search on city (case-insensitive)
CREATE INDEX idx_cvc_city_lower
    ON cv_candidates (lower(city));

-- Keyword search on full_name (case-insensitive)
CREATE INDEX idx_cvc_fullname_lower
    ON cv_candidates (lower(full_name));

-- Title search on work experience titles (case-insensitive)
CREATE INDEX idx_cvw_title_lower
    ON cv_work_experiences (lower(title));

-- Composite index for experience calculation join
CREATE INDEX idx_cvw_candidate_dates
    ON cv_work_experiences (cv_candidate_id, start_date, end_date, is_current);
