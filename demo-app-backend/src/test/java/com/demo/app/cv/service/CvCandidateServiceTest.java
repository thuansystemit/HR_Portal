package com.demo.app.cv.service;

import com.demo.app.content.service.DocumentService;
import com.demo.app.cv.dto.CreateCvCandidateRequest;
import com.demo.app.cv.dto.IngestCvRequest;
import com.demo.app.cv.entity.CvCandidate;
import com.demo.app.cv.extraction.CvExtractionResult;
import com.demo.app.cv.extraction.CvExtractionResultMapper;
import com.demo.app.cv.repository.*;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvCandidateServiceTest {

    @Mock CvCandidateRepository candidateRepository;
    @Mock CvWorkExperienceRepository workExperienceRepository;
    @Mock CvEducationRepository educationRepository;
    @Mock CvTechnicalSkillRepository technicalSkillRepository;
    @Mock CvLanguageRepository languageRepository;
    @Mock CvCertificationRepository certificationRepository;
    @Mock CvExtractionResultMapper resultMapper;
    @Mock DocumentService documentService;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    CvCandidateService cvCandidateService;

    private final UUID CANDIDATE_ID = UUID.randomUUID();
    private final UUID DOC_ID = UUID.randomUUID();
    private final UUID CAT_ID = UUID.randomUUID();

    @Test
    void create_savesCandidate_andChildEntities() {
        var request = buildRequest();
        var saved = buildCandidate();

        when(candidateRepository.existsByDocumentId(DOC_ID)).thenReturn(false);
        when(candidateRepository.save(any())).thenReturn(saved);
        when(workExperienceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(educationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(technicalSkillRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(languageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(certificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(workExperienceRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(educationRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(technicalSkillRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(languageRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(certificationRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());

        var result = cvCandidateService.create(request);

        assertThat(result.id()).isEqualTo(CANDIDATE_ID);
        verify(candidateRepository).save(any());
        verify(workExperienceRepository).save(any());
        verify(educationRepository).save(any());
        verify(technicalSkillRepository).save(any());
        verify(languageRepository).save(any());
        verify(certificationRepository).save(any());
    }

    @Test
    void create_throws_whenDocumentAlreadyExtracted() {
        var request = buildRequest();
        when(candidateRepository.existsByDocumentId(DOC_ID)).thenReturn(true);

        assertThatThrownBy(() -> cvCandidateService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already extracted");

        verify(candidateRepository, never()).save(any());
    }

    @Test
    void create_handlesNullChildLists() {
        var request = new CreateCvCandidateRequest(
                DOC_ID, CAT_ID, "Jane Doe", null, null, null, null,
                null, null, null, null, null, null, null, null,
                "HIGH", null, null, null, Instant.now(),
                null, null, null, null, null
        );
        var saved = buildCandidate();

        when(candidateRepository.existsByDocumentId(DOC_ID)).thenReturn(false);
        when(candidateRepository.save(any())).thenReturn(saved);
        when(workExperienceRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(educationRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(technicalSkillRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(languageRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(certificationRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());

        // Should not throw NPE
        var result = cvCandidateService.create(request);

        assertThat(result).isNotNull();
        verify(workExperienceRepository, never()).save(any());
        verify(educationRepository, never()).save(any());
    }

    @Test
    void findById_returnsResponse() {
        var candidate = buildCandidate();
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(workExperienceRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(educationRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(technicalSkillRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(languageRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(certificationRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());

        var result = cvCandidateService.findById(CANDIDATE_ID);

        assertThat(result.id()).isEqualTo(CANDIDATE_ID);
        assertThat(result.fullName()).isEqualTo("John Doe");
    }

    @Test
    void findById_throws_whenNotFound() {
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cvCandidateService.findById(CANDIDATE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByDocumentId_returnsResponse() {
        var candidate = buildCandidate();
        when(candidateRepository.findByDocumentId(DOC_ID)).thenReturn(Optional.of(candidate));
        when(workExperienceRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(educationRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(technicalSkillRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(languageRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(certificationRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());

        var result = cvCandidateService.findByDocumentId(DOC_ID);

        assertThat(result.documentId()).isEqualTo(DOC_ID);
    }

    @Test
    void findByDocumentId_throws_whenNotFound() {
        when(candidateRepository.findByDocumentId(DOC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cvCandidateService.findByDocumentId(DOC_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listByCategory_returnsList() {
        var candidate = buildCandidate();
        when(candidateRepository.findByDocumentCategoryId(CAT_ID)).thenReturn(List.of(candidate));
        when(workExperienceRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(educationRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
        when(technicalSkillRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(languageRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
        when(certificationRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());

        var result = cvCandidateService.listByCategory(CAT_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void delete_removesCandidate() {
        var candidate = buildCandidate();
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));

        cvCandidateService.delete(CANDIDATE_ID);

        verify(candidateRepository).delete(candidate);
    }

    @Test
    void delete_throws_whenNotFound() {
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cvCandidateService.delete(CANDIDATE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ingest_throws_whenPathTraversalDetected() {
        ReflectionTestUtils.setField(cvCandidateService, "outputDir", "/tmp/test-output");
        // "../secret.json" normalizes to /tmp/secret.json which is outside /tmp/test-output
        var request = new IngestCvRequest(DOC_ID, CAT_ID, "../secret.json", null, null);

        assertThatThrownBy(() -> cvCandidateService.ingest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid jsonFile path");
    }

    @Test
    void ingest_updatesStatusToFailed_onIOException() throws Exception {
        ReflectionTestUtils.setField(cvCandidateService, "outputDir", "/tmp/test-output");
        var request = new IngestCvRequest(DOC_ID, CAT_ID, "candidate.json", null, null);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.readAllBytes(any(Path.class)))
                     .thenThrow(new IOException("disk error"));

            assertThatThrownBy(() -> cvCandidateService.ingest(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to read extraction result");

            verify(documentService).updateExtractionStatus(
                    eq(DOC_ID), eq("FAILED"), eq("JSON_READ"), anyString());
        }
    }

    @Test
    void ingest_succeeds_onValidJson() throws Exception {
        ReflectionTestUtils.setField(cvCandidateService, "outputDir", "/tmp/test-output");
        var request = new IngestCvRequest(DOC_ID, CAT_ID, "candidate.json", null, null);
        var extractionResult = new CvExtractionResult(
                "John", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                null, null, null, null, null);
        var createRequest = buildRequest();
        var saved = buildCandidate();

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.readAllBytes(any(Path.class)))
                     .thenReturn("{}".getBytes());

            when(objectMapper.readValue(any(byte[].class), eq(CvExtractionResult.class)))
                    .thenReturn(extractionResult);
            when(resultMapper.toRequest(extractionResult, DOC_ID, CAT_ID)).thenReturn(createRequest);
            when(candidateRepository.existsByDocumentId(DOC_ID)).thenReturn(false);
            when(candidateRepository.save(any())).thenReturn(saved);
            when(workExperienceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(educationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(technicalSkillRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(languageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(certificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(workExperienceRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
            when(educationRepository.findByCvCandidateIdOrderBySortOrder(CANDIDATE_ID)).thenReturn(List.of());
            when(technicalSkillRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
            when(languageRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());
            when(certificationRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());

            var result = cvCandidateService.ingest(request);

            assertThat(result.id()).isEqualTo(CANDIDATE_ID);
            verify(documentService).updateExtractionStatus(DOC_ID, "COMPLETED");
        }
    }

    private CvCandidate buildCandidate() {
        return CvCandidate.builder()
                .id(CANDIDATE_ID)
                .documentId(DOC_ID)
                .documentCategoryId(CAT_ID)
                .fullName("John Doe")
                .confidenceOverall("HIGH")
                .extractedAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    private CreateCvCandidateRequest buildRequest() {
        return new CreateCvCandidateRequest(
                DOC_ID, CAT_ID, "John Doe", "john@example.com", "+1234567890",
                "New York", "US", null, null, null,
                "Experienced developer", List.of("Spring Boot"), List.of("Teamwork"),
                null, null, "HIGH", null, null, "en", Instant.now(),
                List.of(new CreateCvCandidateRequest.WorkExperienceRequest(
                        "Acme Corp", "Senior Dev", null, null, null, false,
                        null, null, null, null, null)),
                List.of(new CreateCvCandidateRequest.EducationRequest(
                        "MIT", "BS", "CS", (short) 2010, (short) 2014, null, null)),
                List.of("Java"),
                List.of(new CreateCvCandidateRequest.LanguageRequest("English", "Native")),
                List.of(new CreateCvCandidateRequest.CertificationRequest(
                        "AWS Cert", "Amazon", null, null, "CERT123"))
        );
    }
}
