package com.demo.app.recruitment;

import com.demo.app.cv.entity.CandidateHiringStatus;
import com.demo.app.cv.entity.CvCandidate;
import com.demo.app.cv.repository.CvCandidateRepository;
import com.demo.app.recruitment.entity.JobApplication;
import com.demo.app.recruitment.repository.JobApplicationRepository;
import com.demo.app.recruitment.service.CandidateHiringStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateHiringStatusServiceTest {

    @Mock CvCandidateRepository candidateRepository;
    @Mock JobApplicationRepository applicationRepository;

    @InjectMocks
    CandidateHiringStatusService service;

    private final UUID CANDIDATE_ID = UUID.randomUUID();

    @Test
    void recalculate_doesNothing_whenCandidateNotFound() {
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

        service.recalculate(CANDIDATE_ID);

        verify(applicationRepository, never()).findByCvCandidateId(any());
        verify(candidateRepository, never()).save(any());
    }

    @Test
    void recalculate_setsAvailable_whenNoApplications() {
        var candidate = buildCandidate();
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(applicationRepository.findByCvCandidateId(CANDIDATE_ID)).thenReturn(List.of());

        service.recalculate(CANDIDATE_ID);

        assertThat(candidate.getHiringStatus()).isEqualTo(CandidateHiringStatus.AVAILABLE);
        verify(candidateRepository).save(candidate);
    }

    @Test
    void recalculate_setsHired_whenHiredStageExists() {
        var candidate = buildCandidate();
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(applicationRepository.findByCvCandidateId(CANDIDATE_ID))
                .thenReturn(List.of(buildApp("APPLIED"), buildApp("HIRED")));

        service.recalculate(CANDIDATE_ID);

        assertThat(candidate.getHiringStatus()).isEqualTo(CandidateHiringStatus.HIRED);
    }

    @Test
    void recalculate_setsOffered_whenOfferStageExistsButNotHired() {
        var candidate = buildCandidate();
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(applicationRepository.findByCvCandidateId(CANDIDATE_ID))
                .thenReturn(List.of(buildApp("OFFER"), buildApp("REJECTED")));

        service.recalculate(CANDIDATE_ID);

        assertThat(candidate.getHiringStatus()).isEqualTo(CandidateHiringStatus.OFFERED);
    }

    @Test
    void recalculate_setsInProcess_whenActiveStageExists() {
        var candidate = buildCandidate();
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(applicationRepository.findByCvCandidateId(CANDIDATE_ID))
                .thenReturn(List.of(buildApp("SCREENING"), buildApp("REJECTED")));

        service.recalculate(CANDIDATE_ID);

        assertThat(candidate.getHiringStatus()).isEqualTo(CandidateHiringStatus.IN_PROCESS);
    }

    @Test
    void recalculate_setsInProcess_whenInterviewStageExists() {
        var candidate = buildCandidate();
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(applicationRepository.findByCvCandidateId(CANDIDATE_ID))
                .thenReturn(List.of(buildApp("INTERVIEW")));

        service.recalculate(CANDIDATE_ID);

        assertThat(candidate.getHiringStatus()).isEqualTo(CandidateHiringStatus.IN_PROCESS);
    }

    @Test
    void recalculate_setsRejected_whenOnlyRejectedApplications() {
        var candidate = buildCandidate();
        when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
        when(applicationRepository.findByCvCandidateId(CANDIDATE_ID))
                .thenReturn(List.of(buildApp("REJECTED"), buildApp("REJECTED")));

        service.recalculate(CANDIDATE_ID);

        assertThat(candidate.getHiringStatus()).isEqualTo(CandidateHiringStatus.REJECTED);
    }

    private CvCandidate buildCandidate() {
        return CvCandidate.builder()
                .id(CANDIDATE_ID)
                .fullName("Jane Doe")
                .documentId(UUID.randomUUID())
                .documentCategoryId(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private JobApplication buildApp(String stage) {
        return JobApplication.builder()
                .id(UUID.randomUUID())
                .jobPostingId(UUID.randomUUID())
                .cvCandidateId(CANDIDATE_ID)
                .stage(stage)
                .appliedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
