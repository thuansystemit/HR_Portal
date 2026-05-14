package com.demo.app.cv.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateCvCandidateRequest(
        @NotNull UUID documentId,
        @NotNull UUID documentCategoryId,
        @NotBlank @Size(max = 300) String fullName,
        @Email @Size(max = 320) String email,
        @Size(max = 20) String phone,
        @Size(max = 200) String city,
        @Size(max = 2) String country,
        @Size(max = 500) String linkedinUrl,
        @Size(max = 500) String githubUrl,
        @Size(max = 500) String portfolioUrl,
        String summary,
        List<String> toolsAndFrameworks,
        List<String> softSkills,
        List<Map<String, Object>> projects,
        List<Map<String, Object>> publications,
        @NotBlank String confidenceOverall,
        List<String> lowConfidenceFields,
        List<String> missingFields,
        String rawLanguage,
        Instant extractedAt,
        @Valid List<WorkExperienceRequest> workExperiences,
        @Valid List<EducationRequest> educations,
        List<String> technicalSkills,
        List<LanguageRequest> languages,
        @Valid List<CertificationRequest> certifications
) {

    public record WorkExperienceRequest(
            @NotBlank String company,
            @NotBlank String title,
            LocalDate startDate,
            String startDatePrecision,
            LocalDate endDate,
            boolean isCurrent,
            String location,
            Boolean isRemote,
            List<String> responsibilities,
            List<String> achievements,
            List<String> technologies
    ) {}

    public record EducationRequest(
            @NotBlank String institution,
            @NotBlank String degree,
            String fieldOfStudy,
            Short startYear,
            Short endYear,
            BigDecimal gpa,
            String honors
    ) {}

    public record LanguageRequest(
            @NotBlank String language,
            String proficiency
    ) {}

    public record CertificationRequest(
            @NotBlank String name,
            String issuer,
            LocalDate issuedDate,
            LocalDate expiryDate,
            String credentialId
    ) {}
}
