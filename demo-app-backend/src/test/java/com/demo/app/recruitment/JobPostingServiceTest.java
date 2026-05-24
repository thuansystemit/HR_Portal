package com.demo.app.recruitment;

import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.recruitment.dto.*;
import com.demo.app.recruitment.entity.JobPosting;
import com.demo.app.recruitment.repository.JobApplicationRepository;
import com.demo.app.recruitment.repository.JobPostingRepository;
import com.demo.app.recruitment.service.JobPostingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

    @Mock JobPostingRepository jobPostingRepository;
    @Mock JobApplicationRepository jobApplicationRepository;

    @InjectMocks
    JobPostingService jobPostingService;

    private final UUID POSTING_ID = UUID.randomUUID();
    private final UUID USER_ID    = UUID.randomUUID();

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void create_savesAndReturnsResponse() {
        var req = new CreateJobPostingRequest("Senior Dev", "Engineering", "Remote",
                "Description", "Requirements", LocalDate.now().plusDays(30), "OPEN");
        var saved = buildPosting("OPEN");

        when(jobPostingRepository.save(any())).thenReturn(saved);

        var result = jobPostingService.create(req, USER_ID);

        assertThat(result.id()).isEqualTo(POSTING_ID);
        assertThat(result.title()).isEqualTo("Senior Dev");
        assertThat(result.status()).isEqualTo("OPEN");
        assertThat(result.applicationCount()).isZero();
        verify(jobPostingRepository).save(any());
    }

    @Test
    void create_defaultsToDraft_whenStatusNull() {
        var req = new CreateJobPostingRequest("Junior Dev", null, null, null, null, null, null);
        var saved = buildPosting("DRAFT");

        when(jobPostingRepository.save(any())).thenReturn(saved);

        var result = jobPostingService.create(req, USER_ID);

        assertThat(result.status()).isEqualTo("DRAFT");
    }

    // ── list ────────────────────────────────────────────────────────────────

    @Test
    void list_withStatusFilter_callsFindByStatus() {
        var posting = buildPosting("OPEN");
        var page = new PageImpl<>(List.of(posting));
        var pageable = PageRequest.of(0, 20);

        when(jobPostingRepository.findByStatus("OPEN", pageable)).thenReturn(page);
        when(jobApplicationRepository.countByJobPostingId(POSTING_ID)).thenReturn(5);

        var result = jobPostingService.list("OPEN", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).applicationCount()).isEqualTo(5);
        verify(jobPostingRepository).findByStatus("OPEN", pageable);
        verify(jobPostingRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void list_withoutStatusFilter_callsFindAll() {
        var page = new PageImpl<>(List.of(buildPosting("DRAFT")));
        var pageable = PageRequest.of(0, 20);

        when(jobPostingRepository.findAll(pageable)).thenReturn(page);
        when(jobApplicationRepository.countByJobPostingId(POSTING_ID)).thenReturn(0);

        var result = jobPostingService.list(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(jobPostingRepository).findAll(pageable);
        verify(jobPostingRepository, never()).findByStatus(any(), any());
    }

    @Test
    void list_withBlankStatus_callsFindAll() {
        var page = new PageImpl<>(List.of(buildPosting("DRAFT")));
        var pageable = PageRequest.of(0, 20);

        when(jobPostingRepository.findAll(pageable)).thenReturn(page);
        when(jobApplicationRepository.countByJobPostingId(POSTING_ID)).thenReturn(0);

        var result = jobPostingService.list("  ", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(jobPostingRepository).findAll(pageable);
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_returnsResponse_whenFound() {
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(buildPosting("OPEN")));
        when(jobApplicationRepository.countByJobPostingId(POSTING_ID)).thenReturn(3);

        var result = jobPostingService.findById(POSTING_ID);

        assertThat(result.id()).isEqualTo(POSTING_ID);
        assertThat(result.applicationCount()).isEqualTo(3);
    }

    @Test
    void findById_throws_whenNotFound() {
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingService.findById(POSTING_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(POSTING_ID.toString());
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_changesOnlyNonNullFields() {
        var posting = buildPosting("DRAFT");
        var req = new UpdateJobPostingRequest("Updated Title", null, null, null, null, null, "OPEN");

        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(jobPostingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobApplicationRepository.countByJobPostingId(POSTING_ID)).thenReturn(0);

        var result = jobPostingService.update(POSTING_ID, req);

        assertThat(posting.getTitle()).isEqualTo("Updated Title");
        assertThat(posting.getStatus()).isEqualTo("OPEN");
        assertThat(result.title()).isEqualTo("Updated Title");
    }

    @Test
    void update_updatesAllFields_whenAllProvided() {
        var posting = buildPosting("DRAFT");
        var deadline = LocalDate.now().plusDays(60);
        var req = new UpdateJobPostingRequest("New Title", "HR", "NYC", "Desc", "Req", deadline, "OPEN");

        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(jobPostingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobApplicationRepository.countByJobPostingId(POSTING_ID)).thenReturn(0);

        jobPostingService.update(POSTING_ID, req);

        assertThat(posting.getDepartment()).isEqualTo("HR");
        assertThat(posting.getLocation()).isEqualTo("NYC");
        assertThat(posting.getDescription()).isEqualTo("Desc");
        assertThat(posting.getRequirements()).isEqualTo("Req");
        assertThat(posting.getDeadline()).isEqualTo(deadline);
    }

    @Test
    void update_throws_whenNotFound() {
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingService.update(POSTING_ID,
                new UpdateJobPostingRequest("X", null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_setsStatusToClosed() {
        var posting = buildPosting("OPEN");
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(jobPostingRepository.save(any())).thenReturn(posting);

        jobPostingService.delete(POSTING_ID);

        assertThat(posting.getStatus()).isEqualTo("CLOSED");
        verify(jobPostingRepository).save(posting);
    }

    @Test
    void delete_throws_whenNotFound() {
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingService.delete(POSTING_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JobPosting buildPosting(String status) {
        return JobPosting.builder()
                .id(POSTING_ID)
                .title("Senior Dev")
                .department("Engineering")
                .location("Remote")
                .status(status)
                .createdBy(USER_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
