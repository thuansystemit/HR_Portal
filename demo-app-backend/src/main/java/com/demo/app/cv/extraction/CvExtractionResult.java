package com.demo.app.cv.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CvExtractionResult(
        String fullName,
        String email,
        String phone,
        String city,
        String country,
        String linkedinUrl,
        String githubUrl,
        String portfolioUrl,
        String summary,
        List<String> toolsAndFrameworks,
        List<String> softSkills,
        List<Map<String, Object>> projects,
        List<Map<String, Object>> publications,
        String confidenceOverall,
        List<String> lowConfidenceFields,
        List<String> missingFields,
        String rawLanguage,
        List<WorkExperience> workExperiences,
        List<Education> educations,
        List<String> technicalSkills,
        List<Language> languages,
        List<Certification> certifications
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkExperience(
            String company,
            String title,
            String startDate,
            String startDatePrecision,
            String endDate,
            @JsonProperty("isCurrent") boolean isCurrent,
            String location,
            Boolean isRemote,
            List<String> responsibilities,
            List<String> achievements,
            List<String> technologies
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Education(
            String institution,
            String degree,
            String fieldOfStudy,
            Integer startYear,
            Integer endYear,
            BigDecimal gpa,
            String honors
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Language(
            String language,
            String proficiency
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Certification(
            String name,
            String issuer,
            String issuedDate,
            String expiryDate,
            String credentialId
    ) {}
}
