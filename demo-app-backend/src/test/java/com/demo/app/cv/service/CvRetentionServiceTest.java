package com.demo.app.cv.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.cv.entity.CandidateHiringStatus;
import com.demo.app.cv.entity.CvCandidate;
import com.demo.app.cv.repository.CvCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvRetentionServiceTest {

    @Mock CvCandidateRepository cvCandidateRepository;
    @Mock AuditService auditService;

    CvRetentionService service;

    @BeforeEach
    void setUp() {
        service = new CvRetentionService(cvCandidateRepository, auditService);
        ReflectionTestUtils.setField(service, "cvRetentionDays", 1095);
    }

    private CvCandidate candidateWithStatus(CandidateHiringStatus status) {
        return CvCandidate.builder()
                .id(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .documentCategoryId(UUID.randomUUID())
                .fullName("Jane Doe")
                .email("jane@example.com")
                .phone("+1234567890")
                .city("New York")
                .country("US")
                .linkedinUrl("https://linkedin.com/in/jane")
                .summary("Experienced engineer")
                .hiringStatus(status)
                .confidenceOverall("HIGH")
                .build();
    }

    @Test
    void anonymizeExpiredCandidates_anonymizesPiiFields() {
        var candidate = candidateWithStatus(CandidateHiringStatus.AVAILABLE);
        when(cvCandidateRepository.findAnonymizableBefore(any())).thenReturn(List.of(candidate));
        when(cvCandidateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.anonymizeExpiredCandidates();

        assertThat(candidate.getFullName()).isEqualTo("ANONYMIZED");
        assertThat(candidate.getEmail()).isNull();
        assertThat(candidate.getPhone()).isNull();
        assertThat(candidate.getCity()).isNull();
        assertThat(candidate.getCountry()).isNull();
        assertThat(candidate.getLinkedinUrl()).isNull();
        assertThat(candidate.getSummary()).isNull();
        assertThat(candidate.getAnonymizedAt()).isNotNull();
    }

    @Test
    void anonymizeExpiredCandidates_emitsAuditEvent_withCount() {
        var c1 = candidateWithStatus(CandidateHiringStatus.REJECTED);
        var c2 = candidateWithStatus(CandidateHiringStatus.WITHDRAWN);
        when(cvCandidateRepository.findAnonymizableBefore(any())).thenReturn(List.of(c1, c2));
        when(cvCandidateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.anonymizeExpiredCandidates();

        verify(auditService).log(isNull(), eq("CV_BATCH_ANONYMIZED"), eq("CvCandidate"),
                isNull(), isNull(),
                argThat(m -> "2".equals(m.get("count"))),
                eq("success"));
    }

    @Test
    void anonymizeExpiredCandidates_passesCorrectCutoffToRepository() {
        ReflectionTestUtils.setField(service, "cvRetentionDays", 365);
        when(cvCandidateRepository.findAnonymizableBefore(any())).thenReturn(List.of());

        service.anonymizeExpiredCandidates();

        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(cvCandidateRepository).findAnonymizableBefore(captor.capture());
        var expectedCutoff = Instant.now().minusSeconds(365L * 86_400);
        assertThat(captor.getValue()).isBetween(
                expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
    }

    @Test
    void anonymizeExpiredCandidates_skipsRun_whenNoCandidatesFound() {
        when(cvCandidateRepository.findAnonymizableBefore(any())).thenReturn(List.of());

        service.anonymizeExpiredCandidates();

        verify(cvCandidateRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void anonymizeExpiredCandidates_skipsRun_whenRetentionDaysIsZero() {
        ReflectionTestUtils.setField(service, "cvRetentionDays", 0);

        service.anonymizeExpiredCandidates();

        verifyNoInteractions(cvCandidateRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void anonymizeExpiredCandidates_skipsRun_whenRetentionDaysIsNegative() {
        ReflectionTestUtils.setField(service, "cvRetentionDays", -1);

        service.anonymizeExpiredCandidates();

        verifyNoInteractions(cvCandidateRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void anonymizeExpiredCandidates_stampsAnonymizedAt() {
        var before = Instant.now().minusSeconds(1);
        var candidate = candidateWithStatus(CandidateHiringStatus.AVAILABLE);
        when(cvCandidateRepository.findAnonymizableBefore(any())).thenReturn(List.of(candidate));
        when(cvCandidateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.anonymizeExpiredCandidates();

        assertThat(candidate.getAnonymizedAt()).isAfter(before);
    }
}
