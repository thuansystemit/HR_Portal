package com.demo.app.hiring;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.cv.entity.CvCandidate;
import com.demo.app.cv.repository.CvCandidateRepository;
import com.demo.app.hiring.dto.CvShareResponse;
import com.demo.app.hiring.dto.ShareCvRequest;
import com.demo.app.hiring.dto.SubmitImpressionRequest;
import com.demo.app.hiring.entity.CvShare;
import com.demo.app.hiring.entity.HiringRequest;
import com.demo.app.hiring.repository.CvShareRepository;
import com.demo.app.hiring.repository.HiringRequestRepository;
import com.demo.app.hiring.service.CvShareService;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CvShareServiceTest {

    @Mock CvShareRepository cvShareRepository;
    @Mock HiringRequestRepository hiringRequestRepository;
    @Mock CvCandidateRepository cvCandidateRepository;
    @Mock UserRepository userRepository;
    @Mock AuditService auditService;

    @InjectMocks
    CvShareService cvShareService;

    private final UUID HIRING_REQUEST_ID = UUID.randomUUID();
    private final UUID CV_CANDIDATE_ID   = UUID.randomUUID();
    private final UUID SHARED_BY_ID      = UUID.randomUUID();
    private final UUID SHARED_WITH_ID    = UUID.randomUUID();
    private final UUID SHARE_ID          = UUID.randomUUID();

    // ── share ────────────────────────────────────────────────────────────────

    @Test
    void share_savesAndReturnsResponse_whenNoDuplicate() {
        var req = new ShareCvRequest(CV_CANDIDATE_ID, SHARED_WITH_ID, "Looks good");
        var saved = buildShare(null, null);

        when(hiringRequestRepository.findById(HIRING_REQUEST_ID))
                .thenReturn(Optional.of(buildHiringRequest()));
        when(cvShareRepository.existsByHiringRequestIdAndCvCandidateIdAndSharedWith(
                HIRING_REQUEST_ID, CV_CANDIDATE_ID, SHARED_WITH_ID)).thenReturn(false);
        when(cvShareRepository.save(any())).thenReturn(saved);
        when(cvCandidateRepository.findById(CV_CANDIDATE_ID))
                .thenReturn(Optional.of(buildCandidate("John Doe")));
        when(userRepository.findByIdAndDeletedAtIsNull(SHARED_BY_ID))
                .thenReturn(Optional.of(buildUser(SHARED_BY_ID, "HR Manager")));
        when(userRepository.findByIdAndDeletedAtIsNull(SHARED_WITH_ID))
                .thenReturn(Optional.of(buildUser(SHARED_WITH_ID, "Dev Team Member")));

        CvShareResponse result = cvShareService.share(HIRING_REQUEST_ID, req, SHARED_BY_ID);

        assertThat(result.id()).isEqualTo(SHARE_ID);
        assertThat(result.candidateFullName()).isEqualTo("John Doe");
        assertThat(result.sharedByName()).isEqualTo("HR Manager");
        assertThat(result.sharedWithName()).isEqualTo("Dev Team Member");
        verify(cvShareRepository).save(any());
    }

    @Test
    void share_throwsConflict_whenDuplicateExists() {
        var req = new ShareCvRequest(CV_CANDIDATE_ID, SHARED_WITH_ID, null);

        when(hiringRequestRepository.findById(HIRING_REQUEST_ID))
                .thenReturn(Optional.of(buildHiringRequest()));
        when(cvShareRepository.existsByHiringRequestIdAndCvCandidateIdAndSharedWith(
                HIRING_REQUEST_ID, CV_CANDIDATE_ID, SHARED_WITH_ID)).thenReturn(true);

        assertThatThrownBy(() -> cvShareService.share(HIRING_REQUEST_ID, req, SHARED_BY_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already shared");

        verify(cvShareRepository, never()).save(any());
    }

    @Test
    void share_throws_whenHiringRequestNotFound() {
        var req = new ShareCvRequest(CV_CANDIDATE_ID, SHARED_WITH_ID, null);
        when(hiringRequestRepository.findById(HIRING_REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cvShareService.share(HIRING_REQUEST_ID, req, SHARED_BY_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(HIRING_REQUEST_ID.toString());

        verify(cvShareRepository, never()).save(any());
    }

    // ── submitImpression ─────────────────────────────────────────────────────

    @Test
    void submitImpression_updatesImpressionAndReviewedAt_whenValid() {
        var req = new SubmitImpressionRequest("INTERESTED", "Great profile");
        var entity = buildShare(null, null);

        when(cvShareRepository.findById(SHARE_ID)).thenReturn(Optional.of(entity));
        when(cvShareRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cvCandidateRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        CvShareResponse result = cvShareService.submitImpression(SHARE_ID, req, SHARED_WITH_ID);

        assertThat(entity.getImpression()).isEqualTo("INTERESTED");
        assertThat(entity.getComment()).isEqualTo("Great profile");
        assertThat(entity.getReviewedAt()).isNotNull();
        verify(cvShareRepository).save(entity);
    }

    @Test
    void submitImpression_setsNotInterested() {
        var req = new SubmitImpressionRequest("NOT_INTERESTED", "Not a fit");
        var entity = buildShare(null, null);

        when(cvShareRepository.findById(SHARE_ID)).thenReturn(Optional.of(entity));
        when(cvShareRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cvCandidateRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        cvShareService.submitImpression(SHARE_ID, req, SHARED_WITH_ID);

        assertThat(entity.getImpression()).isEqualTo("NOT_INTERESTED");
    }

    @Test
    void submitImpression_setsReviewLater() {
        var req = new SubmitImpressionRequest("REVIEW_LATER", null);
        var entity = buildShare(null, null);

        when(cvShareRepository.findById(SHARE_ID)).thenReturn(Optional.of(entity));
        when(cvShareRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cvCandidateRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        cvShareService.submitImpression(SHARE_ID, req, SHARED_WITH_ID);

        assertThat(entity.getImpression()).isEqualTo("REVIEW_LATER");
        assertThat(entity.getReviewedAt()).isNotNull();
    }

    @Test
    void submitImpression_throwsIllegalArgument_whenImpressionInvalid() {
        var req = new SubmitImpressionRequest("MAYBE", "hmm");

        assertThatThrownBy(() -> cvShareService.submitImpression(SHARE_ID, req, SHARED_WITH_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAYBE");

        verify(cvShareRepository, never()).findById(any());
        verify(cvShareRepository, never()).save(any());
    }

    @Test
    void submitImpression_throws_whenShareNotFound() {
        var req = new SubmitImpressionRequest("INTERESTED", null);
        when(cvShareRepository.findById(SHARE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cvShareService.submitImpression(SHARE_ID, req, SHARED_WITH_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(SHARE_ID.toString());

        verify(cvShareRepository, never()).save(any());
    }

    // ── listByRequest ─────────────────────────────────────────────────────────

    @Test
    void listByRequest_returnsAllSharesForRequest() {
        var s1 = buildShare(null, null);
        var s2 = buildShare("INTERESTED", Instant.now());

        when(cvShareRepository.findByHiringRequestIdOrderBySharedAtDesc(HIRING_REQUEST_ID))
                .thenReturn(List.of(s1, s2));
        when(cvCandidateRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        List<CvShareResponse> result = cvShareService.listByRequest(HIRING_REQUEST_ID);

        assertThat(result).hasSize(2);
        verify(cvShareRepository).findByHiringRequestIdOrderBySharedAtDesc(HIRING_REQUEST_ID);
    }

    // ── listMySharedCvs ───────────────────────────────────────────────────────

    @Test
    void listMySharedCvs_returnsSharesForRecipient() {
        var share = buildShare("INTERESTED", Instant.now());

        when(cvShareRepository.findBySharedWithOrderBySharedAtDesc(SHARED_WITH_ID))
                .thenReturn(List.of(share));
        when(cvCandidateRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        List<CvShareResponse> result = cvShareService.listMySharedCvs(SHARED_WITH_ID);

        assertThat(result).hasSize(1);
        verify(cvShareRepository).findBySharedWithOrderBySharedAtDesc(SHARED_WITH_ID);
    }

    @Test
    void listMySharedCvs_returnsEmpty_whenNone() {
        when(cvShareRepository.findBySharedWithOrderBySharedAtDesc(SHARED_WITH_ID))
                .thenReturn(List.of());

        List<CvShareResponse> result = cvShareService.listMySharedCvs(SHARED_WITH_ID);

        assertThat(result).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CvShare buildShare(String impression, Instant reviewedAt) {
        return CvShare.builder()
                .id(SHARE_ID)
                .hiringRequestId(HIRING_REQUEST_ID)
                .cvCandidateId(CV_CANDIDATE_ID)
                .sharedBy(SHARED_BY_ID)
                .sharedWith(SHARED_WITH_ID)
                .impression(impression)
                .comment(null)
                .sharedAt(Instant.now())
                .reviewedAt(reviewedAt)
                .build();
    }

    private HiringRequest buildHiringRequest() {
        return HiringRequest.builder()
                .id(HIRING_REQUEST_ID)
                .requesterId(UUID.randomUUID())
                .title("Backend Dev")
                .roleType("BACKEND")
                .department("Engineering")
                .urgency("MEDIUM")
                .status("PENDING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private CvCandidate buildCandidate(String fullName) {
        return CvCandidate.builder()
                .id(CV_CANDIDATE_ID)
                .fullName(fullName)
                .documentId(UUID.randomUUID())
                .documentCategoryId(UUID.randomUUID())
                .confidenceOverall("HIGH")
                .build();
    }

    private User buildUser(UUID id, String fullName) {
        return User.builder().id(id).fullName(fullName).email("user@example.com").build();
    }
}
