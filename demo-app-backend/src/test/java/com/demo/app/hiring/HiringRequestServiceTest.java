package com.demo.app.hiring;

import com.demo.app.hiring.dto.CreateHiringRequestRequest;
import com.demo.app.hiring.dto.HiringRequestResponse;
import com.demo.app.hiring.dto.UpdateHiringRequestStatusRequest;
import com.demo.app.hiring.entity.HiringRequest;
import com.demo.app.hiring.repository.HiringRequestRepository;
import com.demo.app.hiring.service.HiringRequestService;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.repository.UserRepository;
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
class HiringRequestServiceTest {

    @Mock HiringRequestRepository hiringRequestRepository;
    @Mock UserRepository userRepository;

    @InjectMocks
    HiringRequestService hiringRequestService;

    private final UUID REQUEST_ID   = UUID.randomUUID();
    private final UUID REQUESTER_ID = UUID.randomUUID();

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void create_savesAndReturnsResponse_whenValidInput() {
        var req = new CreateHiringRequestRequest(
                "Backend Dev", "We need a backend dev", "BACKEND", "Engineering", "HIGH");
        var saved = buildRequest("PENDING", "BACKEND", "HIGH");
        var requester = buildUser(REQUESTER_ID, "Alice Smith");

        when(hiringRequestRepository.save(any())).thenReturn(saved);
        when(userRepository.findByIdAndDeletedAtIsNull(REQUESTER_ID))
                .thenReturn(Optional.of(requester));

        HiringRequestResponse result = hiringRequestService.create(req, REQUESTER_ID);

        assertThat(result.id()).isEqualTo(REQUEST_ID);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.roleType()).isEqualTo("BACKEND");
        assertThat(result.urgency()).isEqualTo("HIGH");
        assertThat(result.requesterName()).isEqualTo("Alice Smith");
        verify(hiringRequestRepository).save(any());
    }

    @Test
    void create_defaultsUrgencyToMedium_whenUrgencyNull() {
        var req = new CreateHiringRequestRequest(
                "Frontend Dev", null, "FRONTEND", "Design", null);
        var saved = buildRequest("PENDING", "FRONTEND", "MEDIUM");
        when(hiringRequestRepository.save(any())).thenReturn(saved);
        when(userRepository.findByIdAndDeletedAtIsNull(REQUESTER_ID)).thenReturn(Optional.empty());

        HiringRequestResponse result = hiringRequestService.create(req, REQUESTER_ID);

        assertThat(result.urgency()).isEqualTo("MEDIUM");
    }

    @Test
    void create_throwsIllegalArgument_whenRoleTypeInvalid() {
        var req = new CreateHiringRequestRequest(
                "Dev", null, "DEVOPS", "Infra", "MEDIUM");

        assertThatThrownBy(() -> hiringRequestService.create(req, REQUESTER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEVOPS");

        verify(hiringRequestRepository, never()).save(any());
    }

    @Test
    void create_throwsIllegalArgument_whenUrgencyInvalid() {
        var req = new CreateHiringRequestRequest(
                "Dev", null, "BACKEND", "Infra", "EXTREME");

        assertThatThrownBy(() -> hiringRequestService.create(req, REQUESTER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXTREME");

        verify(hiringRequestRepository, never()).save(any());
    }

    // ── listByRequester ──────────────────────────────────────────────────────

    @Test
    void listByRequester_returnsListForUser() {
        var r1 = buildRequest("PENDING", "BACKEND", "MEDIUM");
        var r2 = buildRequest("IN_PROGRESS", "FRONTEND", "HIGH");

        when(hiringRequestRepository.findByRequesterIdOrderByCreatedAtDesc(REQUESTER_ID))
                .thenReturn(List.of(r1, r2));
        when(userRepository.findByIdAndDeletedAtIsNull(REQUESTER_ID)).thenReturn(Optional.empty());

        List<HiringRequestResponse> result = hiringRequestService.listByRequester(REQUESTER_ID);

        assertThat(result).hasSize(2);
        verify(hiringRequestRepository).findByRequesterIdOrderByCreatedAtDesc(REQUESTER_ID);
    }

    @Test
    void listByRequester_returnsEmpty_whenNoRequests() {
        when(hiringRequestRepository.findByRequesterIdOrderByCreatedAtDesc(REQUESTER_ID))
                .thenReturn(List.of());

        List<HiringRequestResponse> result = hiringRequestService.listByRequester(REQUESTER_ID);

        assertThat(result).isEmpty();
    }

    // ── listAll ──────────────────────────────────────────────────────────────

    @Test
    void listAll_returnsAllRequests() {
        var r1 = buildRequest("PENDING", "BACKEND", "MEDIUM");
        var r2 = buildRequest("CLOSED", "FULLSTACK", "LOW");

        when(hiringRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(r1, r2));
        when(userRepository.findByIdAndDeletedAtIsNull(REQUESTER_ID)).thenReturn(Optional.empty());

        List<HiringRequestResponse> result = hiringRequestService.listAll();

        assertThat(result).hasSize(2);
        verify(hiringRequestRepository).findAllByOrderByCreatedAtDesc();
    }

    // ── updateStatus ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_updatesStatusSuccessfully_whenValid() {
        var entity = buildRequest("PENDING", "BACKEND", "MEDIUM");
        var req = new UpdateHiringRequestStatusRequest("IN_PROGRESS", null);

        when(hiringRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(entity));
        when(hiringRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByIdAndDeletedAtIsNull(REQUESTER_ID)).thenReturn(Optional.empty());

        HiringRequestResponse result = hiringRequestService.updateStatus(REQUEST_ID, req, REQUESTER_ID);

        assertThat(entity.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        verify(hiringRequestRepository).save(entity);
    }

    @Test
    void updateStatus_linksJobPosting_whenJobPostingIdProvided() {
        UUID jobPostingId = UUID.randomUUID();
        var entity = buildRequest("PENDING", "BACKEND", "MEDIUM");
        var req = new UpdateHiringRequestStatusRequest("CANDIDATE_FOUND", jobPostingId);

        when(hiringRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(entity));
        when(hiringRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByIdAndDeletedAtIsNull(REQUESTER_ID)).thenReturn(Optional.empty());

        HiringRequestResponse result = hiringRequestService.updateStatus(REQUEST_ID, req, REQUESTER_ID);

        assertThat(entity.getJobPostingId()).isEqualTo(jobPostingId);
        assertThat(result.jobPostingId()).isEqualTo(jobPostingId);
    }

    @Test
    void updateStatus_throwsIllegalArgument_whenStatusInvalid() {
        var req = new UpdateHiringRequestStatusRequest("UNKNOWN", null);

        assertThatThrownBy(() -> hiringRequestService.updateStatus(REQUEST_ID, req, REQUESTER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");

        verify(hiringRequestRepository, never()).findById(any());
        verify(hiringRequestRepository, never()).save(any());
    }

    @Test
    void updateStatus_throws_whenNotFound() {
        var req = new UpdateHiringRequestStatusRequest("IN_PROGRESS", null);
        when(hiringRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hiringRequestService.updateStatus(REQUEST_ID, req, REQUESTER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(REQUEST_ID.toString());

        verify(hiringRequestRepository, never()).save(any());
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_returnsResponse_whenFound() {
        var entity = buildRequest("PENDING", "FULLSTACK", "CRITICAL");
        when(hiringRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(entity));
        when(userRepository.findByIdAndDeletedAtIsNull(REQUESTER_ID)).thenReturn(Optional.empty());

        HiringRequestResponse result = hiringRequestService.getById(REQUEST_ID);

        assertThat(result.id()).isEqualTo(REQUEST_ID);
        assertThat(result.roleType()).isEqualTo("FULLSTACK");
    }

    @Test
    void getById_throws_whenNotFound() {
        when(hiringRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hiringRequestService.getById(REQUEST_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(REQUEST_ID.toString());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private HiringRequest buildRequest(String status, String roleType, String urgency) {
        return HiringRequest.builder()
                .id(REQUEST_ID)
                .requesterId(REQUESTER_ID)
                .title("Dev Position")
                .description("Description")
                .roleType(roleType)
                .department("Engineering")
                .urgency(urgency)
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private User buildUser(UUID id, String fullName) {
        return User.builder().id(id).fullName(fullName).email("user@example.com").build();
    }
}
