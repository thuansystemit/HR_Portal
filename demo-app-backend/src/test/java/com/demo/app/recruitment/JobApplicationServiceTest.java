package com.demo.app.recruitment;

import com.demo.app.cv.entity.CvCandidate;
import com.demo.app.cv.repository.CvCandidateRepository;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.recruitment.dto.CreateApplicationRequest;
import com.demo.app.recruitment.dto.MoveStageRequest;
import com.demo.app.recruitment.entity.JobApplication;
import com.demo.app.recruitment.entity.JobPosting;
import com.demo.app.recruitment.repository.ApplicationStageHistoryRepository;
import com.demo.app.recruitment.repository.JobApplicationRepository;
import com.demo.app.recruitment.repository.JobPostingRepository;
import com.demo.app.recruitment.service.CandidateHiringStatusService;
import com.demo.app.recruitment.service.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock JobPostingRepository jobPostingRepository;
    @Mock JobApplicationRepository jobApplicationRepository;
    @Mock ApplicationStageHistoryRepository stageHistoryRepository;
    @Mock CvCandidateRepository cvCandidateRepository;
    @Mock CandidateHiringStatusService hiringStatusService;

    @InjectMocks
    JobApplicationService jobApplicationService;

    private final UUID POSTING_ID = UUID.randomUUID();
    private final UUID APP_ID     = UUID.randomUUID();
    private final UUID CANDIDATE_ID = UUID.randomUUID();
    private final UUID MOVER_ID   = UUID.randomUUID();

    // ── apply ─────────────────────────────────────────────────────────────────

    @Test
    void apply_success_createsApplicationAndHistory() {
        var posting = buildPosting("OPEN");
        var req = new CreateApplicationRequest(CANDIDATE_ID, "Looking good");
        var saved = buildApplication("APPLIED");
        var candidate = buildCandidate();

        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(jobApplicationRepository.existsByJobPostingIdAndCvCandidateId(POSTING_ID, CANDIDATE_ID))
                .thenReturn(false);
        when(jobApplicationRepository.save(any())).thenReturn(saved);
        when(stageHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cvCandidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));

        var result = jobApplicationService.apply(POSTING_ID, req, MOVER_ID);

        assertThat(result.id()).isEqualTo(APP_ID);
        assertThat(result.stage()).isEqualTo("APPLIED");
        assertThat(result.candidateFullName()).isEqualTo("Jane Doe");
        verify(stageHistoryRepository).save(any());
    }

    @Test
    void apply_throws_whenPostingNotFound() {
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.apply(POSTING_ID,
                new CreateApplicationRequest(CANDIDATE_ID, null), MOVER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    void apply_throws_whenPostingIsDraft() {
        var posting = buildPosting("DRAFT");
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> jobApplicationService.apply(POSTING_ID,
                new CreateApplicationRequest(CANDIDATE_ID, null), MOVER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not open");

        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    void apply_throws_whenPostingIsClosed() {
        var posting = buildPosting("CLOSED");
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> jobApplicationService.apply(POSTING_ID,
                new CreateApplicationRequest(CANDIDATE_ID, null), MOVER_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void apply_throws_whenDuplicateApplication() {
        var posting = buildPosting("OPEN");
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(jobApplicationRepository.existsByJobPostingIdAndCvCandidateId(POSTING_ID, CANDIDATE_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> jobApplicationService.apply(POSTING_ID,
                new CreateApplicationRequest(CANDIDATE_ID, null), MOVER_ID))
                .isInstanceOf(ConflictException.class);

        verify(jobApplicationRepository, never()).save(any());
    }

    // ── listByPosting ─────────────────────────────────────────────────────────

    @Test
    void listByPosting_returnsApplicationsWithCandidateInfo() {
        var app = buildApplication("APPLIED");
        var candidate = buildCandidate();

        when(jobApplicationRepository.findByJobPostingId(POSTING_ID)).thenReturn(List.of(app));
        when(cvCandidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));

        var result = jobApplicationService.listByPosting(POSTING_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).candidateFullName()).isEqualTo("Jane Doe");
        assertThat(result.get(0).candidateEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void listByPosting_handlesNullCandidate_whenCandidateNotFound() {
        var app = buildApplication("APPLIED");

        when(jobApplicationRepository.findByJobPostingId(POSTING_ID)).thenReturn(List.of(app));
        when(cvCandidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

        var result = jobApplicationService.listByPosting(POSTING_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).candidateFullName()).isNull();
        assertThat(result.get(0).candidateEmail()).isNull();
    }

    @Test
    void listByPosting_returnsEmpty_whenNoApplications() {
        when(jobApplicationRepository.findByJobPostingId(POSTING_ID)).thenReturn(List.of());

        var result = jobApplicationService.listByPosting(POSTING_ID);

        assertThat(result).isEmpty();
    }

    // ── getBoard ──────────────────────────────────────────────────────────────

    @Test
    void getBoard_groupsApplicationsByStage() {
        var applied     = buildApplicationWithStage("APPLIED");
        var screening   = buildApplicationWithStage("SCREENING");
        var interview   = buildApplicationWithStage("INTERVIEW");
        var hired       = buildApplicationWithStage("HIRED");
        var rejected    = buildApplicationWithStage("REJECTED");

        when(jobApplicationRepository.findByJobPostingId(POSTING_ID))
                .thenReturn(List.of(applied, screening, interview, hired, rejected));
        when(cvCandidateRepository.findById(any())).thenReturn(Optional.empty());

        var result = jobApplicationService.getBoard(POSTING_ID);

        assertThat(result.columns()).containsKeys(
                "APPLIED", "SCREENING", "INTERVIEW", "OFFER", "HIRED", "REJECTED");
        assertThat(result.columns().get("APPLIED")).hasSize(1);
        assertThat(result.columns().get("OFFER")).isEmpty();
        assertThat(result.columns().get("HIRED")).hasSize(1);
    }

    @Test
    void getBoard_returnsAllStageColumns_evenWhenEmpty() {
        when(jobApplicationRepository.findByJobPostingId(POSTING_ID)).thenReturn(List.of());

        var result = jobApplicationService.getBoard(POSTING_ID);

        assertThat(result.columns()).hasSize(6);
        assertThat(result.columns().get("APPLIED")).isEmpty();
        assertThat(result.columns().get("REJECTED")).isEmpty();
    }

    // ── moveStage ─────────────────────────────────────────────────────────────

    @Test
    void moveStage_updatesStageAndSavesHistory() {
        var app = buildApplication("APPLIED");
        var req = new MoveStageRequest("SCREENING", "Looks good");
        var candidate = buildCandidate();

        when(jobApplicationRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stageHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cvCandidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));

        var result = jobApplicationService.moveStage(POSTING_ID, APP_ID, req, MOVER_ID);

        assertThat(app.getStage()).isEqualTo("SCREENING");
        assertThat(result.stage()).isEqualTo("SCREENING");
        verify(stageHistoryRepository).save(argThat(h ->
                "APPLIED".equals(h.getFromStage()) && "SCREENING".equals(h.getToStage())));
    }

    @Test
    void moveStage_throws_whenInvalidStage() {
        assertThatThrownBy(() -> jobApplicationService.moveStage(POSTING_ID, APP_ID,
                new MoveStageRequest("INVALID_STAGE", null), MOVER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid stage");

        verify(jobApplicationRepository, never()).findById(any());
    }

    @Test
    void moveStage_throws_whenApplicationNotFound() {
        when(jobApplicationRepository.findById(APP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.moveStage(POSTING_ID, APP_ID,
                new MoveStageRequest("SCREENING", null), MOVER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void moveStage_throws_whenApplicationBelongsToDifferentPosting() {
        var otherPostingId = UUID.randomUUID();
        var app = JobApplication.builder()
                .id(APP_ID)
                .jobPostingId(otherPostingId) // different posting
                .cvCandidateId(CANDIDATE_ID)
                .stage("APPLIED")
                .appliedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(jobApplicationRepository.findById(APP_ID)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> jobApplicationService.moveStage(POSTING_ID, APP_ID,
                new MoveStageRequest("SCREENING", null), MOVER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JobPosting buildPosting(String status) {
        return JobPosting.builder()
                .id(POSTING_ID)
                .title("Senior Dev")
                .status(status)
                .createdBy(MOVER_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private JobApplication buildApplication(String stage) {
        return JobApplication.builder()
                .id(APP_ID)
                .jobPostingId(POSTING_ID)
                .cvCandidateId(CANDIDATE_ID)
                .stage(stage)
                .appliedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private JobApplication buildApplicationWithStage(String stage) {
        return JobApplication.builder()
                .id(UUID.randomUUID())
                .jobPostingId(POSTING_ID)
                .cvCandidateId(CANDIDATE_ID)
                .stage(stage)
                .appliedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private CvCandidate buildCandidate() {
        return CvCandidate.builder()
                .id(CANDIDATE_ID)
                .fullName("Jane Doe")
                .email("jane@example.com")
                .confidenceOverall("HIGH")
                .documentId(UUID.randomUUID())
                .documentCategoryId(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
