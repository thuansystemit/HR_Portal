package com.demo.app.cv.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CvSearchCriteriaTest {

    @Test
    void withDefaults_appliesDefaultValues() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, null, null, null);

        assertThat(criteria.skills()).isEmpty();
        assertThat(criteria.title()).isNull();
        assertThat(criteria.location()).isNull();
        assertThat(criteria.minYearsExperience()).isNull();
        assertThat(criteria.keyword()).isNull();
        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(20);
        assertThat(criteria.sortBy()).isEqualTo("relevanceScore");
    }

    @Test
    void withDefaults_parsesCommaSeparatedSkills() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                "Java, Spring Boot, React", null, null, null, null, null, null, null);

        assertThat(criteria.skills()).containsExactly("Java", "Spring Boot", "React");
    }

    @Test
    void withDefaults_handlesEmptySkillString() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                "", null, null, null, null, null, null, null);

        assertThat(criteria.skills()).isEmpty();
        assertThat(criteria.hasSkills()).isFalse();
    }

    @Test
    void withDefaults_handlesBlankSkillEntries() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                "Java,,  , Spring", null, null, null, null, null, null, null);

        assertThat(criteria.skills()).containsExactly("Java", "Spring");
    }

    @Test
    void withDefaults_capsPageSizeAt100() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, null, 200, null);

        assertThat(criteria.size()).isEqualTo(100);
    }

    @Test
    void withDefaults_trimsWhitespaceFromTextFields() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, "  Senior Developer  ", "  New York  ", null, "  cloud  ", null, null, null);

        assertThat(criteria.title()).isEqualTo("Senior Developer");
        assertThat(criteria.location()).isEqualTo("New York");
        assertThat(criteria.keyword()).isEqualTo("cloud");
    }

    @Test
    void withDefaults_treatsBlankStringsAsNull() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, "   ", "  ", null, "", null, null, null);

        assertThat(criteria.title()).isNull();
        assertThat(criteria.location()).isNull();
        assertThat(criteria.keyword()).isNull();
        assertThat(criteria.hasTitle()).isFalse();
        assertThat(criteria.hasLocation()).isFalse();
        assertThat(criteria.hasKeyword()).isFalse();
    }

    @Test
    void hasAnyCriteria_returnsFalse_whenNoCriteria() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, null, null, null);

        assertThat(criteria.hasAnyCriteria()).isFalse();
    }

    @Test
    void hasAnyCriteria_returnsTrue_whenSkillsProvided() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                "Java", null, null, null, null, null, null, null);

        assertThat(criteria.hasAnyCriteria()).isTrue();
        assertThat(criteria.hasSkills()).isTrue();
    }

    @Test
    void hasMinExperience_returnsFalse_forZero() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, 0, null, null, null, null);

        assertThat(criteria.hasMinExperience()).isFalse();
    }

    @Test
    void hasMinExperience_returnsTrue_forPositiveValue() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, 3, null, null, null, null);

        assertThat(criteria.hasMinExperience()).isTrue();
    }

    @Test
    void withDefaults_preservesCustomSortBy() {
        CvSearchCriteria criteria = CvSearchCriteria.withDefaults(
                null, null, null, null, null, null, null, "fullName");

        assertThat(criteria.sortBy()).isEqualTo("fullName");
    }
}
