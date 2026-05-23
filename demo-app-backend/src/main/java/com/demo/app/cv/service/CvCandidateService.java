package com.demo.app.cv.service;

import com.demo.app.content.service.DocumentService;
import com.demo.app.cv.dto.CreateCvCandidateRequest;
import com.demo.app.cv.dto.CvCandidateResponse;
import com.demo.app.cv.dto.IngestCvRequest;
import com.demo.app.cv.entity.*;
import com.demo.app.cv.extraction.CvExtractionResult;
import com.demo.app.cv.extraction.CvExtractionResultMapper;
import com.demo.app.cv.repository.*;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CvCandidateService {

    private final CvCandidateRepository candidateRepository;
    private final CvWorkExperienceRepository workExperienceRepository;
    private final CvEducationRepository educationRepository;
    private final CvTechnicalSkillRepository technicalSkillRepository;
    private final CvLanguageRepository languageRepository;
    private final CvCertificationRepository certificationRepository;
    private final CvExtractionResultMapper resultMapper;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    @Value("${app.cv.output-dir:/app/output}")
    private String outputDir;

    public CvCandidateResponse ingest(IngestCvRequest request) {
        String extractorStatus = request.extractionStatus();

        // REJECTED or ERROR from extractor — mark document FAILED, no JSON to read
        if ("REJECTED".equals(extractorStatus) || "ERROR".equals(extractorStatus)) {
            String warnings = request.guardrailWarnings() != null
                    ? String.join("; ", request.guardrailWarnings()) : extractorStatus;
            documentService.updateExtractionStatus(request.documentId(), "FAILED",
                    extractorStatus, warnings);
            return null;
        }

        if (request.jsonFile() == null || request.jsonFile().isBlank()) {
            documentService.updateExtractionStatus(request.documentId(), "FAILED",
                    "INGEST", "jsonFile is missing");
            throw new IllegalArgumentException("jsonFile is required for successful extraction");
        }

        Path jsonPath = Paths.get(outputDir).resolve(request.jsonFile()).normalize();
        if (!jsonPath.startsWith(Paths.get(outputDir).normalize())) {
            throw new IllegalArgumentException("Invalid jsonFile path");
        }
        try {
            byte[] bytes = Files.readAllBytes(jsonPath);
            CvExtractionResult result = objectMapper.readValue(bytes, CvExtractionResult.class);
            CreateCvCandidateRequest createRequest = resultMapper.toRequest(
                    result, request.documentId(), request.documentCategoryId());
            CvCandidateResponse response = create(createRequest);
            documentService.updateExtractionStatus(request.documentId(), "COMPLETED");
            return response;
        } catch (IOException e) {
            documentService.updateExtractionStatus(request.documentId(), "FAILED",
                    "JSON_READ", e.getMessage());
            throw new RuntimeException("Failed to read extraction result: " + request.jsonFile(), e);
        }
    }

    public CvCandidateResponse create(CreateCvCandidateRequest request) {
        if (candidateRepository.existsByDocumentId(request.documentId())) {
            throw new ConflictException("CV already extracted for document: " + request.documentId());
        }

        var candidate = CvCandidate.builder()
                .documentId(request.documentId())
                .documentCategoryId(request.documentCategoryId())
                .fullName(request.fullName())
                .email(request.email())
                .phone(request.phone())
                .city(request.city())
                .country(request.country())
                .linkedinUrl(request.linkedinUrl())
                .githubUrl(request.githubUrl())
                .portfolioUrl(request.portfolioUrl())
                .summary(request.summary())
                .toolsAndFrameworks(request.toolsAndFrameworks())
                .softSkills(request.softSkills())
                .projects(request.projects())
                .publications(request.publications())
                .confidenceOverall(request.confidenceOverall())
                .lowConfidenceFields(request.lowConfidenceFields())
                .missingFields(request.missingFields())
                .rawLanguage(request.rawLanguage())
                .extractedAt(request.extractedAt() != null ? request.extractedAt() : java.time.Instant.now())
                .build();

        var saved = candidateRepository.save(candidate);

        saveWorkExperiences(saved.getId(), request);
        saveEducations(saved.getId(), request);
        saveTechnicalSkills(saved.getId(), request);
        saveLanguages(saved.getId(), request);
        saveCertifications(saved.getId(), request);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CvCandidateResponse findById(UUID id) {
        var candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CvCandidate", id));
        return toResponse(candidate);
    }

    @Transactional(readOnly = true)
    public CvCandidateResponse findByDocumentId(UUID documentId) {
        var candidate = candidateRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CvCandidate not found for documentId: " + documentId));
        return toResponse(candidate);
    }

    @Transactional(readOnly = true)
    public List<CvCandidateResponse> listByCategory(UUID categoryId) {
        return candidateRepository.findByDocumentCategoryId(categoryId).stream()
                .map(this::toResponse)
                .toList();
    }

    public void delete(UUID id) {
        var candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CvCandidate", id));
        candidateRepository.delete(candidate);
    }

    private void saveWorkExperiences(UUID candidateId, CreateCvCandidateRequest request) {
        if (request.workExperiences() == null) return;
        for (int i = 0; i < request.workExperiences().size(); i++) {
            var req = request.workExperiences().get(i);
            var entity = CvWorkExperience.builder()
                    .cvCandidateId(candidateId)
                    .sortOrder((short) i)
                    .company(req.company())
                    .title(req.title())
                    .startDate(req.startDate())
                    .startDatePrecision(req.startDatePrecision())
                    .endDate(req.endDate())
                    .isCurrent(req.isCurrent())
                    .location(req.location())
                    .isRemote(req.isRemote())
                    .responsibilities(req.responsibilities())
                    .achievements(req.achievements())
                    .technologies(req.technologies())
                    .build();
            workExperienceRepository.save(entity);
        }
    }

    private void saveEducations(UUID candidateId, CreateCvCandidateRequest request) {
        if (request.educations() == null) return;
        for (int i = 0; i < request.educations().size(); i++) {
            var req = request.educations().get(i);
            var entity = CvEducation.builder()
                    .cvCandidateId(candidateId)
                    .sortOrder((short) i)
                    .institution(req.institution())
                    .degree(req.degree())
                    .fieldOfStudy(req.fieldOfStudy())
                    .startYear(req.startYear())
                    .endYear(req.endYear())
                    .gpa(req.gpa())
                    .honors(req.honors())
                    .build();
            educationRepository.save(entity);
        }
    }

    private void saveTechnicalSkills(UUID candidateId, CreateCvCandidateRequest request) {
        if (request.technicalSkills() == null) return;
        for (var skill : request.technicalSkills()) {
            if (skill == null || skill.isBlank()) continue;
            var entity = CvTechnicalSkill.builder()
                    .cvCandidateId(candidateId)
                    .skillName(skill)
                    .build();
            technicalSkillRepository.save(entity);
        }
    }

    private void saveLanguages(UUID candidateId, CreateCvCandidateRequest request) {
        if (request.languages() == null) return;
        for (var lang : request.languages()) {
            var entity = CvLanguage.builder()
                    .cvCandidateId(candidateId)
                    .language(lang.language())
                    .proficiency(lang.proficiency())
                    .build();
            languageRepository.save(entity);
        }
    }

    private void saveCertifications(UUID candidateId, CreateCvCandidateRequest request) {
        if (request.certifications() == null) return;
        for (var cert : request.certifications()) {
            var entity = CvCertification.builder()
                    .cvCandidateId(candidateId)
                    .name(cert.name())
                    .issuer(cert.issuer())
                    .issuedDate(cert.issuedDate())
                    .expiryDate(cert.expiryDate())
                    .credentialId(cert.credentialId())
                    .build();
            certificationRepository.save(entity);
        }
    }

    private CvCandidateResponse toResponse(CvCandidate c) {
        var workExperiences = workExperienceRepository
                .findByCvCandidateIdOrderBySortOrder(c.getId()).stream()
                .map(w -> new CvCandidateResponse.WorkExperienceResponse(
                        w.getId(), w.getSortOrder(), w.getCompany(), w.getTitle(),
                        w.getStartDate(), w.getStartDatePrecision(), w.getEndDate(),
                        w.isCurrent(), w.getLocation(), w.getIsRemote(),
                        w.getResponsibilities(), w.getAchievements(), w.getTechnologies()))
                .toList();

        var educations = educationRepository
                .findByCvCandidateIdOrderBySortOrder(c.getId()).stream()
                .map(e -> new CvCandidateResponse.EducationResponse(
                        e.getId(), e.getSortOrder(), e.getInstitution(), e.getDegree(),
                        e.getFieldOfStudy(), e.getStartYear(), e.getEndYear(),
                        e.getGpa(), e.getHonors()))
                .toList();

        var technicalSkills = technicalSkillRepository
                .findByCvCandidateId(c.getId()).stream()
                .map(CvTechnicalSkill::getSkillName)
                .toList();

        var languages = languageRepository
                .findByCvCandidateId(c.getId()).stream()
                .map(l -> new CvCandidateResponse.LanguageResponse(
                        l.getId(), l.getLanguage(), l.getProficiency()))
                .toList();

        var certifications = certificationRepository
                .findByCvCandidateId(c.getId()).stream()
                .map(cert -> new CvCandidateResponse.CertificationResponse(
                        cert.getId(), cert.getName(), cert.getIssuer(),
                        cert.getIssuedDate(), cert.getExpiryDate(), cert.getCredentialId()))
                .toList();

        return new CvCandidateResponse(
                c.getId(), c.getDocumentId(), c.getDocumentCategoryId(),
                c.getFullName(), c.getEmail(), c.getPhone(), c.getCity(), c.getCountry(),
                c.getLinkedinUrl(), c.getGithubUrl(), c.getPortfolioUrl(), c.getSummary(),
                c.getToolsAndFrameworks(), c.getSoftSkills(), c.getProjects(), c.getPublications(),
                c.getConfidenceOverall(), c.getLowConfidenceFields(), c.getMissingFields(),
                c.getRawLanguage(), c.getExtractedAt(), c.getCreatedAt(),
                workExperiences, educations, technicalSkills, languages, certifications);
    }
}
