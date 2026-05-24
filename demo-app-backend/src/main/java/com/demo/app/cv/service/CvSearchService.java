package com.demo.app.cv.service;

import com.demo.app.cv.dto.CvSearchCriteria;
import com.demo.app.cv.dto.CvSearchResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs multi-criteria candidate search with weighted relevance scoring.
 * <p>
 * Relevance weights:
 *   skills=40%, title=25%, keyword=20%, location=10%, experience=5%
 * <p>
 * Uses native SQL via NamedParameterJdbcTemplate because the scoring formula
 * and multi-table joins are not expressible cleanly through JPA Specification.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CvSearchService {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Search candidates with relevance ranking.
     * <p>
     * When no search criteria are provided, all candidates are returned with
     * a relevance score of 0, sorted by full_name ascending.
     */
    public Page<CvSearchResultResponse> search(CvSearchCriteria criteria) {
        // Build count query first
        var countParams = new MapSqlParameterSource();
        var countSql = buildSearchSql(criteria, countParams, true);

        Long total = jdbc.queryForObject(countSql, countParams, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), PageRequest.of(criteria.page(), criteria.size()), 0);
        }

        // Build data query with fresh params
        var dataParams = new MapSqlParameterSource();
        var sql = buildSearchSql(criteria, dataParams, false);

        List<CvSearchResultResponse> results = jdbc.query(sql, dataParams, this::mapRow);

        return new PageImpl<>(results, PageRequest.of(criteria.page(), criteria.size()), total);
    }

    private String buildSearchSql(CvSearchCriteria criteria, MapSqlParameterSource params, boolean countOnly) {
        var sb = new StringBuilder();

        // -- CTE: experience_years per candidate
        sb.append("""
            WITH exp AS (
                SELECT cv_candidate_id,
                       COALESCE(SUM(
                           EXTRACT(EPOCH FROM (
                               AGE(COALESCE(end_date, CURRENT_DATE), COALESCE(start_date, CURRENT_DATE))
                           )) / 31557600.0
                       ), 0) AS total_years
                FROM cv_work_experiences
                GROUP BY cv_candidate_id
            ),
            current_title AS (
                SELECT DISTINCT ON (cv_candidate_id)
                       cv_candidate_id, title
                FROM cv_work_experiences
                ORDER BY cv_candidate_id, sort_order ASC
            )
            """);

        // -- Skill match CTE (only if skills are provided)
        if (criteria.hasSkills()) {
            sb.append("""
            , skill_match AS (
                SELECT ts.cv_candidate_id,
                       COUNT(DISTINCT ts.skill_name_lower) AS matched_count
                FROM cv_technical_skills ts
                WHERE
            """);
            List<String> skillConditions = new ArrayList<>();
            for (int i = 0; i < criteria.skills().size(); i++) {
                String paramName = "skill_" + i;
                // Word-boundary regex: \m = start-of-word, \M = end-of-word.
                // Matches "Java", "Java EE", "Java 17" but NOT "JavaScript".
                skillConditions.add("ts.skill_name_lower ~* :" + paramName);
                params.addValue(paramName, "\\m" + escapeRegex(criteria.skills().get(i).toLowerCase()) + "\\M");
            }
            sb.append(String.join(" OR ", skillConditions));
            sb.append("""

                GROUP BY ts.cv_candidate_id
            )
            """);
            params.addValue("total_skills", criteria.skills().size());
        }

        // -- Main SELECT
        if (countOnly) {
            sb.append("SELECT COUNT(*) FROM (");
        }

        sb.append("""
            SELECT c.id AS candidate_id,
                   c.full_name,
                   c.email,
                   c.city,
                   c.country,
                   ct.title AS current_title,
                   ROUND(COALESCE(exp.total_years, 0)::NUMERIC, 1) AS total_experience_years,
                   c.document_id,
                   c.document_category_id,
            """);

        // -- Relevance score computation
        sb.append(buildRelevanceScoreSql(criteria));

        sb.append("""
            FROM cv_candidates c
            LEFT JOIN exp ON exp.cv_candidate_id = c.id
            LEFT JOIN current_title ct ON ct.cv_candidate_id = c.id
            """);

        if (criteria.hasSkills()) {
            sb.append("LEFT JOIN skill_match sm ON sm.cv_candidate_id = c.id\n");
        }

        // -- WHERE clause
        sb.append("WHERE 1=1\n");

        if (criteria.hasSkills()) {
            sb.append("AND sm.cv_candidate_id IS NOT NULL\n");
        }

        if (criteria.hasMinExperience()) {
            sb.append("AND COALESCE(exp.total_years, 0) >= :min_experience\n");
            params.addValue("min_experience", criteria.minYearsExperience());
        }

        if (criteria.hasLocation()) {
            sb.append("AND (LOWER(c.city) ILIKE :location OR LOWER(c.country) ILIKE :location)\n");
            params.addValue("location", "%" + criteria.location().toLowerCase() + "%");
        }

        if (criteria.hasTitle()) {
            sb.append("""
                AND EXISTS (
                    SELECT 1 FROM cv_work_experiences we
                    WHERE we.cv_candidate_id = c.id
                    AND LOWER(we.title) ILIKE :title_filter
                )
                """);
            params.addValue("title_filter", "%" + criteria.title().toLowerCase() + "%");
        }

        if (criteria.hasKeyword()) {
            sb.append("""
                AND (
                    LOWER(c.full_name) ILIKE :keyword
                    OR LOWER(c.summary) ILIKE :keyword
                    OR EXISTS (
                        SELECT 1 FROM cv_work_experiences wk
                        WHERE wk.cv_candidate_id = c.id
                        AND (
                            LOWER(wk.company) ILIKE :keyword
                            OR LOWER(wk.title) ILIKE :keyword
                        )
                    )
                    OR EXISTS (
                        SELECT 1 FROM cv_technical_skills tsk
                        WHERE tsk.cv_candidate_id = c.id
                        AND tsk.skill_name_lower ILIKE :keyword
                    )
                )
                """);
            params.addValue("keyword", "%" + criteria.keyword().toLowerCase() + "%");
        }

        if (countOnly) {
            sb.append(") AS cnt");
        } else {
            // -- ORDER BY
            sb.append("ORDER BY ");
            switch (criteria.sortBy()) {
                case "fullName" -> sb.append("c.full_name ASC");
                case "experienceYears" -> sb.append("total_experience_years DESC");
                default -> sb.append("relevance_score DESC, c.full_name ASC");
            }

            // -- PAGINATION
            sb.append("\nLIMIT :limit OFFSET :offset\n");
            params.addValue("limit", criteria.size());
            params.addValue("offset", criteria.page() * criteria.size());
        }

        return sb.toString();
    }

    private String buildRelevanceScoreSql(CvSearchCriteria criteria) {
        if (!criteria.hasAnyCriteria()) {
            return "0 AS relevance_score\n";
        }

        var parts = new ArrayList<String>();

        // Skill match: 40% weight
        if (criteria.hasSkills()) {
            parts.add("COALESCE(sm.matched_count, 0)::FLOAT / :total_skills * 40.0");
        }

        // Title match: 25% weight
        if (criteria.hasTitle()) {
            parts.add("""
                CASE WHEN EXISTS (
                    SELECT 1 FROM cv_work_experiences wt
                    WHERE wt.cv_candidate_id = c.id
                    AND LOWER(wt.title) ILIKE :title_filter
                ) THEN 25.0 ELSE 0.0 END""");
        }

        // Keyword match: 20% weight
        if (criteria.hasKeyword()) {
            parts.add("""
                CASE WHEN (
                    LOWER(c.full_name) ILIKE :keyword
                    OR LOWER(c.summary) ILIKE :keyword
                    OR EXISTS (
                        SELECT 1 FROM cv_work_experiences wkr
                        WHERE wkr.cv_candidate_id = c.id
                        AND (LOWER(wkr.company) ILIKE :keyword OR LOWER(wkr.title) ILIKE :keyword)
                    )
                    OR EXISTS (
                        SELECT 1 FROM cv_technical_skills tskr
                        WHERE tskr.cv_candidate_id = c.id
                        AND tskr.skill_name_lower ILIKE :keyword
                    )
                ) THEN 20.0 ELSE 0.0 END""");
        }

        // Location match: 10% weight
        if (criteria.hasLocation()) {
            parts.add("""
                CASE WHEN (
                    LOWER(c.city) ILIKE :location
                    OR LOWER(c.country) ILIKE :location
                ) THEN 10.0 ELSE 0.0 END""");
        }

        // Experience match: 5% weight
        if (criteria.hasMinExperience()) {
            parts.add("""
                CASE WHEN COALESCE(exp.total_years, 0) >= :min_experience
                THEN 5.0 ELSE 0.0 END""");
        }

        // Normalize: divide by max possible score and scale to 0-100
        double maxPossible = 0;
        if (criteria.hasSkills()) maxPossible += 40;
        if (criteria.hasTitle()) maxPossible += 25;
        if (criteria.hasKeyword()) maxPossible += 20;
        if (criteria.hasLocation()) maxPossible += 10;
        if (criteria.hasMinExperience()) maxPossible += 5;

        return "ROUND((" + String.join(" + ", parts) + ") / " + maxPossible + " * 100.0) AS relevance_score\n";
    }

    private CvSearchResultResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        // Fetch top skills for this candidate
        List<String> topSkills = fetchTopSkills(rs.getObject("candidate_id", java.util.UUID.class));

        return new CvSearchResultResponse(
                rs.getObject("candidate_id", java.util.UUID.class),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("city"),
                rs.getString("country"),
                rs.getString("current_title"),
                rs.getDouble("total_experience_years"),
                topSkills,
                rs.getInt("relevance_score"),
                rs.getObject("document_id", java.util.UUID.class),
                rs.getObject("document_category_id", java.util.UUID.class)
        );
    }

    private List<String> fetchTopSkills(java.util.UUID candidateId) {
        return jdbc.queryForList(
                "SELECT skill_name FROM cv_technical_skills WHERE cv_candidate_id = :candidateId ORDER BY skill_name LIMIT 10",
                new MapSqlParameterSource("candidateId", candidateId),
                String.class
        );
    }

    private static String escapeRegex(String s) {
        // Escape POSIX regex metacharacters so skill names like "C++" or "C#" are treated literally.
        return s.replaceAll("([\\^$.|?*+()\\[\\]{}\\\\])", "\\\\$1");
    }
}
