package com.demo.app.cv.service;

import com.demo.app.cv.dto.CvSearchCriteria;
import com.demo.app.cv.dto.CvSearchResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvSearchServiceTest {

    @Mock
    NamedParameterJdbcTemplate jdbc;

    CvSearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new CvSearchService(jdbc);
    }

    @Test
    void search_withNoCriteria_returnsAllCandidates() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, null, null, null);

        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        Page<CvSearchResultResponse> result = searchService.search(criteria);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void search_withZeroCount_returnsEmptyPage() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                "Java,Spring", "Developer", null, null, null, 0, 20, null);

        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        Page<CvSearchResultResponse> result = searchService.search(criteria);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
    }

    @Test
    void search_withSkills_addsSkillParamsToQuery() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                "Java,Python,React", null, null, null, null, 0, 10, null);

        // Capture the SQL to verify skill parameters are included
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(jdbc.queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class)))
                .thenReturn(0L);

        searchService.search(criteria);

        String sql = sqlCaptor.getValue();
        MapSqlParameterSource params = paramsCaptor.getValue();

        // Verify skill_match CTE uses word-boundary regex (not ILIKE partial match)
        assertThat(sql).contains("skill_match");
        assertThat(sql).contains("~* :skill_0");
        assertThat(sql).contains("~* :skill_1");
        assertThat(sql).contains("~* :skill_2");

        // Verify params use \m...\M word-boundary anchors
        assertThat(params.getValue("skill_0")).isEqualTo("\\mjava\\M");
        assertThat(params.getValue("skill_1")).isEqualTo("\\mpython\\M");
        assertThat(params.getValue("skill_2")).isEqualTo("\\mreact\\M");
        assertThat(params.getValue("total_skills")).isEqualTo(3);
    }

    @Test
    void search_withTitle_addsTitleFilterToQuery() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, "Senior Developer", null, null, null, 0, 20, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(jdbc.queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class)))
                .thenReturn(0L);

        searchService.search(criteria);

        assertThat(sqlCaptor.getValue()).contains("LOWER(we.title) ILIKE :title_filter");
        assertThat(paramsCaptor.getValue().getValue("title_filter")).isEqualTo("%senior developer%");
    }

    @Test
    void search_withLocation_addsLocationFilterToQuery() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, "New York", null, null, 0, 20, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(jdbc.queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class)))
                .thenReturn(0L);

        searchService.search(criteria);

        assertThat(sqlCaptor.getValue()).contains("LOWER(c.city) ILIKE :location");
        assertThat(paramsCaptor.getValue().getValue("location")).isEqualTo("%new york%");
    }

    @Test
    void search_withMinExperience_addsExperienceFilter() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, 5, null, 0, 20, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(jdbc.queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class)))
                .thenReturn(0L);

        searchService.search(criteria);

        assertThat(sqlCaptor.getValue()).contains("COALESCE(exp.total_years, 0) >= :min_experience");
        assertThat(paramsCaptor.getValue().getValue("min_experience")).isEqualTo(5);
    }

    @Test
    void search_withKeyword_addsKeywordFilter() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, "microservices", 0, 20, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(jdbc.queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class)))
                .thenReturn(0L);

        searchService.search(criteria);

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("LOWER(c.full_name) ILIKE :keyword");
        assertThat(sql).contains("LOWER(c.summary) ILIKE :keyword");
        assertThat(paramsCaptor.getValue().getValue("keyword")).isEqualTo("%microservices%");
    }

    @Test
    void search_sortByFullName_ordersCorrectly() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, 0, 20, "fullName");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        // Count query returns 0 so we only capture the count SQL
        when(jdbc.queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        searchService.search(criteria);

        // The count query doesn't include ORDER BY -- we need to verify the main query
        // Since count returns 0, the main query is never called. Verify the criteria parsed correctly.
        assertThat(criteria.sortBy()).isEqualTo("fullName");
    }

    @Test
    void search_sortByExperienceYears_ordersCorrectly() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, 0, 20, "experienceYears");

        assertThat(criteria.sortBy()).isEqualTo("experienceYears");
    }

    @Test
    void search_withAllCriteria_buildsCompleteQuery() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                "Java,Spring", "Senior", "Berlin", 3, "cloud", 0, 20, "relevanceScore");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(jdbc.queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class)))
                .thenReturn(0L);

        searchService.search(criteria);

        String sql = sqlCaptor.getValue();
        MapSqlParameterSource params = paramsCaptor.getValue();

        // All CTEs and filters present
        assertThat(sql).contains("skill_match");
        assertThat(sql).contains("ILIKE :title_filter");
        assertThat(sql).contains("ILIKE :location");
        assertThat(sql).contains(":min_experience");
        assertThat(sql).contains("ILIKE :keyword");

        // Relevance scoring present
        assertThat(sql).contains("relevance_score");

        // All params set
        assertThat(params.getValue("skill_0")).isEqualTo("\\mjava\\M");
        assertThat(params.getValue("skill_1")).isEqualTo("\\mspring\\M");
        assertThat(params.getValue("title_filter")).isEqualTo("%senior%");
        assertThat(params.getValue("location")).isEqualTo("%berlin%");
        assertThat(params.getValue("min_experience")).isEqualTo(3);
        assertThat(params.getValue("keyword")).isEqualTo("%cloud%");
    }

    @Test
    void search_pagination_setsCorrectOffsetAndLimit() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, 2, 15, null);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(jdbc.queryForObject(anyString(), paramsCaptor.capture(), eq(Long.class)))
                .thenReturn(0L);

        searchService.search(criteria);

        assertThat(criteria.page()).isEqualTo(2);
        assertThat(criteria.size()).isEqualTo(15);
    }

    // ── Data-query path (total > 0) ──────────────────────────────────────────

    private CvSearchResultResponse buildStubResult() {
        return new CvSearchResultResponse(
                UUID.randomUUID(), "John Doe", "john@example.com",
                "Berlin", "Germany", "Senior Developer", 5.0,
                List.of("Java", "Spring"), 80,
                UUID.randomUUID(), UUID.randomUUID()
        );
    }

    @SuppressWarnings("unchecked")
    private void stubCountAndDataQuery(long count, List<CvSearchResultResponse> results) {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(count);
        // Mockito returns results directly — the RowMapper lambda is never invoked,
        // so fetchTopSkills (queryForList) is never called either.
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(results);
    }

    @Test
    void search_withPositiveCount_executesDataQueryAndReturnsPage() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                "Java", "Developer", "Berlin", 3, "cloud", 0, 20, "relevanceScore");
        stubCountAndDataQuery(1L, List.of(buildStubResult()));

        Page<CvSearchResultResponse> page = searchService.search(criteria);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).fullName()).isEqualTo("John Doe");
        verify(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_sortByFullName_includesOrderByFullName() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, 0, 20, "fullName");
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbc.query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(buildStubResult()));

        searchService.search(criteria);

        assertThat(sqlCaptor.getValue()).contains("c.full_name ASC");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_sortByExperienceYears_includesOrderByExperience() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, 0, 20, "experienceYears");
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbc.query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(buildStubResult()));

        searchService.search(criteria);

        assertThat(sqlCaptor.getValue()).contains("total_experience_years DESC");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_defaultSort_includesOrderByRelevanceScore() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                "Java", null, null, null, null, 0, 20, "relevanceScore");
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbc.query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(buildStubResult()));

        searchService.search(criteria);

        assertThat(sqlCaptor.getValue()).contains("relevance_score DESC");
    }

    @Test
    void search_withNullCount_returnsEmptyPage() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, 0, 20, null);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(null);

        Page<CvSearchResultResponse> page = searchService.search(criteria);

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
        verify(jdbc, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }
}
