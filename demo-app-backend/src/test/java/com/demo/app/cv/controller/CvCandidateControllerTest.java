package com.demo.app.cv.controller;

import com.demo.app.cv.dto.CvCandidateResponse;
import com.demo.app.cv.dto.CvCandidateSimpleResult;
import com.demo.app.cv.dto.IngestCvRequest;
import com.demo.app.cv.dto.UpdateHiringStatusRequest;
import com.demo.app.cv.service.CvCandidateService;
import com.demo.app.recruitment.dto.ApplicationResponse;
import com.demo.app.recruitment.service.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvCandidateControllerTest {

    @Mock
    private CvCandidateService cvCandidateService;

    @Mock
    private JobApplicationService jobApplicationService;

    @InjectMocks
    private CvCandidateController cvCandidateController;

    private final UUID CANDIDATE_ID = UUID.randomUUID();
    private final UUID DOC_ID = UUID.randomUUID();
    private final UUID CAT_ID = UUID.randomUUID();

    private CvCandidateResponse buildResponse() {
        return new CvCandidateResponse(
                CANDIDATE_ID, DOC_ID, CAT_ID, "John Doe",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null,
                Instant.now(), Instant.now(),
                "AVAILABLE",
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void ingest_returnsCreated() {
        var request = new IngestCvRequest(DOC_ID, CAT_ID, "candidate.json", null, null);
        var response = buildResponse();

        when(cvCandidateService.ingest(request)).thenReturn(response);

        var result = cvCandidateController.ingest(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getFirst("Location")).contains(CANDIDATE_ID.toString());
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void findById_returnsOk() {
        var response = buildResponse();
        when(cvCandidateService.findById(CANDIDATE_ID)).thenReturn(response);

        var result = cvCandidateController.findById(CANDIDATE_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void findByDocumentId_returnsOk() {
        var response = buildResponse();
        when(cvCandidateService.findByDocumentId(DOC_ID)).thenReturn(response);

        var result = cvCandidateController.findByDocumentId(DOC_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void listByCategory_returnsOk() {
        var response = buildResponse();
        when(cvCandidateService.listByCategory(CAT_ID)).thenReturn(List.of(response));

        var result = cvCandidateController.listByCategory(CAT_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void ingest_returnsOk_whenServiceReturnsNull() {
        var request = new IngestCvRequest(DOC_ID, CAT_ID, null, "REJECTED", null);
        when(cvCandidateService.ingest(request)).thenReturn(null);

        var result = cvCandidateController.ingest(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNull();
    }

    @Test
    void delete_returns204() {
        var result = cvCandidateController.delete(CANDIDATE_ID);

        verify(cvCandidateService).delete(CANDIDATE_ID);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void searchSimple_returnsMatchingCandidates() {
        var hit = new CvCandidateSimpleResult(CANDIDATE_ID, "John Doe", "john@example.com");
        when(cvCandidateService.searchSimple("John", 10)).thenReturn(List.of(hit));

        var result = cvCandidateController.searchSimple("John", 10);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).fullName()).isEqualTo("John Doe");
    }

    @Test
    void searchSimple_returnsEmptyList_whenNoMatch() {
        when(cvCandidateService.searchSimple("zzz", 10)).thenReturn(List.of());

        var result = cvCandidateController.searchSimple("zzz", 10);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void updateHiringStatus_returnsOk() {
        var response = buildResponse();
        var request = new UpdateHiringStatusRequest("HIRED");
        when(cvCandidateService.updateHiringStatus(CANDIDATE_ID, request)).thenReturn(response);

        var result = cvCandidateController.updateHiringStatus(CANDIDATE_ID, request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void listApplications_returnsOk() {
        var app = new ApplicationResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Software Engineer",
                CANDIDATE_ID, CAT_ID, "John Doe", "john@example.com",
                "APPLIED", null, java.time.Instant.now(), null, null);
        when(jobApplicationService.listByCandidateId(CANDIDATE_ID)).thenReturn(List.of(app));

        var result = cvCandidateController.listApplications(CANDIDATE_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
    }
}
