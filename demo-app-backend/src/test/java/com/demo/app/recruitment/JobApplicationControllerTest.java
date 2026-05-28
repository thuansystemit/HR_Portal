package com.demo.app.recruitment;

import com.demo.app.recruitment.controller.JobApplicationController;
import com.demo.app.recruitment.dto.ApplicationResponse;
import com.demo.app.recruitment.dto.BatchApplyRequest;
import com.demo.app.recruitment.dto.BatchApplyResult;
import com.demo.app.recruitment.dto.BoardResponse;
import com.demo.app.recruitment.dto.CreateApplicationRequest;
import com.demo.app.recruitment.dto.MoveStageRequest;
import com.demo.app.recruitment.service.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationControllerTest {

    @Mock JobApplicationService jobApplicationService;
    @Mock Authentication authentication;

    @InjectMocks
    JobApplicationController controller;

    private final UUID POSTING_ID   = UUID.randomUUID();
    private final UUID APP_ID       = UUID.randomUUID();
    private final UUID CANDIDATE_ID = UUID.randomUUID();
    private final UUID USER_ID      = UUID.randomUUID();

    @Test
    void apply_returns201_withLocation() {
        when(authentication.getName()).thenReturn(USER_ID.toString());
        var req = new CreateApplicationRequest(CANDIDATE_ID, "Looks great");
        var response = buildResponse();
        when(jobApplicationService.apply(POSTING_ID, req, USER_ID)).thenReturn(response);

        var result = controller.apply(POSTING_ID, req, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void list_returns200() {
        var response = buildResponse();
        when(jobApplicationService.listByPosting(POSTING_ID)).thenReturn(List.of(response));

        var result = controller.list(POSTING_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void board_returns200() {
        var board = new BoardResponse(Map.of("APPLIED", List.of(buildResponse())));
        when(jobApplicationService.getBoard(POSTING_ID)).thenReturn(board);

        var result = controller.board(POSTING_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().columns()).containsKey("APPLIED");
    }

    @Test
    void moveStage_returns200() {
        when(authentication.getName()).thenReturn(USER_ID.toString());
        var req = new MoveStageRequest("SCREENING", "Good fit");
        var response = buildResponse();
        when(jobApplicationService.moveStage(POSTING_ID, APP_ID, req, USER_ID)).thenReturn(response);

        var result = controller.moveStage(POSTING_ID, APP_ID, req, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void batchApply_returns200_withResult() {
        when(authentication.getName()).thenReturn(USER_ID.toString());
        var req = new BatchApplyRequest(List.of(CANDIDATE_ID), "Bulk");
        var batchResult = new BatchApplyResult(List.of(buildResponse()), List.of());
        when(jobApplicationService.batchApply(POSTING_ID, req, USER_ID)).thenReturn(batchResult);

        var result = controller.batchApply(POSTING_ID, req, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().applied()).hasSize(1);
        assertThat(result.getBody().skipped()).isEmpty();
    }

    private ApplicationResponse buildResponse() {
        return new ApplicationResponse(APP_ID, POSTING_ID, "Software Engineer", CANDIDATE_ID,
                UUID.randomUUID(),
                "Jane Doe", "jane@example.com", "APPLIED", null,
                Instant.now(), Instant.now(), null);
    }
}
