package com.demo.app.cv.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

/**
 * Query parameters for the CV candidate search endpoint.
 * All fields are optional -- an empty search returns all candidates ranked by relevance.
 */
public record CvSearchCriteria(
        List<String> skills,
        String title,
        String location,
        @Min(0) Integer minYearsExperience,
        String keyword,
        @Min(0) int page,
        @Min(1) @Max(100) int size,
        @Pattern(regexp = "relevanceScore|fullName|experienceYears")
        String sortBy,
        UUID forJobPostingId
) {

    /**
     * Factory with defaults applied (used by controller to normalize nulls).
     */
    public static CvSearchCriteria withDefaults(
            String skills, String title, String location,
            Integer minYearsExperience, String keyword,
            Integer page, Integer size, String sortBy,
            UUID forJobPostingId
    ) {
        List<String> skillList = (skills == null || skills.isBlank())
                ? List.of()
                : List.of(skills.split(",")).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

        return new CvSearchCriteria(
                skillList,
                trimToNull(title),
                trimToNull(location),
                minYearsExperience,
                trimToNull(keyword),
                page == null ? 0 : page,
                size == null ? 20 : Math.min(size, 100),
                sortBy == null ? "relevanceScore" : sortBy,
                forJobPostingId
        );
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    public boolean hasSkills() {
        return skills != null && !skills.isEmpty();
    }

    public boolean hasTitle() {
        return title != null;
    }

    public boolean hasLocation() {
        return location != null;
    }

    public boolean hasMinExperience() {
        return minYearsExperience != null && minYearsExperience > 0;
    }

    public boolean hasKeyword() {
        return keyword != null;
    }

    public boolean hasAnyCriteria() {
        return hasSkills() || hasTitle() || hasLocation() || hasMinExperience() || hasKeyword();
    }

    public boolean hasForJobPostingId() {
        return forJobPostingId != null;
    }
}
