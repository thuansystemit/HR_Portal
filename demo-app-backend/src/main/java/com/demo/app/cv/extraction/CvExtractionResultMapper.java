package com.demo.app.cv.extraction;

import com.demo.app.cv.dto.CreateCvCandidateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class CvExtractionResultMapper {

    public CreateCvCandidateRequest toRequest(CvExtractionResult result, UUID documentId, UUID documentCategoryId) {
        return new CreateCvCandidateRequest(
                documentId,
                documentCategoryId,
                result.fullName() != null ? result.fullName() : "Unknown",
                result.email(),
                result.phone(),
                result.city(),
                result.country(),
                result.linkedinUrl(),
                result.githubUrl(),
                result.portfolioUrl(),
                result.summary(),
                result.toolsAndFrameworks(),
                result.softSkills(),
                result.projects(),
                result.publications(),
                normalizeConfidence(result.confidenceOverall()),
                result.lowConfidenceFields(),
                result.missingFields(),
                result.rawLanguage(),
                Instant.now(),
                mapWorkExperiences(result.workExperiences()),
                mapEducations(result.educations()),
                result.technicalSkills(),
                mapLanguages(result.languages()),
                mapCertifications(result.certifications())
        );
    }

    private String normalizeConfidence(String value) {
        if (value == null) return "LOW";
        return switch (value.toUpperCase()) {
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private List<CreateCvCandidateRequest.WorkExperienceRequest> mapWorkExperiences(
            List<CvExtractionResult.WorkExperience> list) {
        if (list == null) return null;
        return list.stream().map(w -> new CreateCvCandidateRequest.WorkExperienceRequest(
                w.company() != null ? w.company() : "Unknown",
                w.title() != null ? w.title() : "Unknown",
                parseDate(w.startDate()),
                w.startDatePrecision(),
                parseDate(w.endDate()),
                w.isCurrent(),
                w.location(),
                w.isRemote(),
                w.responsibilities(),
                w.achievements(),
                w.technologies()
        )).toList();
    }

    private List<CreateCvCandidateRequest.EducationRequest> mapEducations(
            List<CvExtractionResult.Education> list) {
        if (list == null) return null;
        return list.stream().map(e -> new CreateCvCandidateRequest.EducationRequest(
                e.institution() != null ? e.institution() : "Unknown",
                e.degree() != null ? e.degree() : "Unknown",
                e.fieldOfStudy(),
                toShort(e.startYear()),
                toShort(e.endYear()),
                e.gpa(),
                e.honors()
        )).toList();
    }

    private List<CreateCvCandidateRequest.LanguageRequest> mapLanguages(
            List<CvExtractionResult.Language> list) {
        if (list == null) return null;
        return list.stream()
                .filter(l -> l.language() != null && !l.language().isBlank())
                .map(l -> new CreateCvCandidateRequest.LanguageRequest(
                        l.language(),
                        l.proficiency()
                )).toList();
    }

    private List<CreateCvCandidateRequest.CertificationRequest> mapCertifications(
            List<CvExtractionResult.Certification> list) {
        if (list == null) return null;
        return list.stream().map(c -> new CreateCvCandidateRequest.CertificationRequest(
                c.name() != null ? c.name() : "Unknown",
                c.issuer(),
                parseDate(c.issuedDate()),
                parseDate(c.expiryDate()),
                c.credentialId()
        )).toList();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            log.warn("Could not parse date '{}': {}", date, e.getMessage());
            return null;
        }
    }

    private Short toShort(Integer value) {
        if (value == null) return null;
        return value.shortValue();
    }
}
