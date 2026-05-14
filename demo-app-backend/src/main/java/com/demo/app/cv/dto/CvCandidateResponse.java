package com.demo.app.cv.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CvCandidateResponse(
        UUID id,
        UUID documentId,
        UUID documentCategoryId,
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
        Instant extractedAt,
        Instant createdAt,
        List<WorkExperienceResponse> workExperiences,
        List<EducationResponse> educations,
        List<String> technicalSkills,
        List<LanguageResponse> languages,
        List<CertificationResponse> certifications
) {

    public record WorkExperienceResponse(
            UUID id,
            short sortOrder,
            String company,
            String title,
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

    public record EducationResponse(
            UUID id,
            short sortOrder,
            String institution,
            String degree,
            String fieldOfStudy,
            Short startYear,
            Short endYear,
            BigDecimal gpa,
            String honors
    ) {}

    public record LanguageResponse(
            UUID id,
            String language,
            String proficiency
    ) {}

    public record CertificationResponse(
            UUID id,
            String name,
            String issuer,
            LocalDate issuedDate,
            LocalDate expiryDate,
            String credentialId
    ) {}
}
