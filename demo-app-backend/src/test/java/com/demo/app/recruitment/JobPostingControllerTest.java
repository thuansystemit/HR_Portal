package com.demo.app.recruitment;

import com.demo.app.recruitment.controller.JobPostingController;
import com.demo.app.recruitment.dto.*;
import com.demo.app.recruitment.service.JobPostingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPostingControllerTest {

    @Mock JobPostingService jobPostingService;
    @Mock Authentication authentication;

    @InjectMocks
    JobPostingController controller;

    private final UUID POSTING_ID = UUID.randomUUID();
    private final UUID USER_ID    = UUID.randomUUID();

    @Test
    void create_returns201_withLocation() {
        when(authentication.getName()).thenReturn(USER_ID.toString());
        var req = new CreateJobPostingRequest("Senior Dev", "Eng", "Remote", "Desc", "Req",
                LocalDate.now().plusDays(30), "OPEN");
        var response = buildResponse();
        when(jobPostingService.create(req, USER_ID)).thenReturn(response);

        var result = controller.create(req, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getHeaders().getLocation().toString()).contains(POSTING_ID.toString());
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void list_returns200_withPage() {
        var summary = new JobPostingSummary(POSTING_ID, "Senior Dev", "Eng", "Remote",
                "OPEN", LocalDate.now().plusDays(30), 5);
        var page = new PageImpl<>(List.of(summary));
        var pageable = PageRequest.of(0, 20);

        when(jobPostingService.list("OPEN", pageable)).thenReturn(page);

        var result = controller.list("OPEN", pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getContent()).hasSize(1);
    }

    @Test
    void findById_returns200() {
        var response = buildResponse();
        when(jobPostingService.findById(POSTING_ID)).thenReturn(response);

        var result = controller.findById(POSTING_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void update_returns200() {
        var req = new UpdateJobPostingRequest("Updated", null, null, null, null, null, "OPEN");
        var response = buildResponse();
        when(jobPostingService.update(POSTING_ID, req)).thenReturn(response);

        var result = controller.update(POSTING_ID, req);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void delete_returns204() {
        doNothing().when(jobPostingService).delete(POSTING_ID);

        var result = controller.delete(POSTING_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(jobPostingService).delete(POSTING_ID);
    }

    private JobPostingResponse buildResponse() {
        return new JobPostingResponse(POSTING_ID, "Senior Dev", "Eng", "Remote",
                "Desc", "Req", LocalDate.now().plusDays(30), "OPEN",
                USER_ID, Instant.now(), Instant.now(), 0);
    }
}
