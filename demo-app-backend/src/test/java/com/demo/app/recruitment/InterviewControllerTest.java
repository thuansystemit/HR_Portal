package com.demo.app.recruitment;

import com.demo.app.recruitment.controller.FeedbackController;
import com.demo.app.recruitment.controller.InterviewController;
import com.demo.app.recruitment.dto.*;
import com.demo.app.recruitment.service.InterviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewControllerTest {

    @Mock InterviewService interviewService;
    @Mock Authentication authentication;

    @InjectMocks
    InterviewController interviewController;

    private final UUID APP_ID       = UUID.randomUUID();
    private final UUID INTERVIEW_ID = UUID.randomUUID();
    private final UUID REVIEWER_ID  = UUID.randomUUID();
    private final UUID USER_ID      = UUID.randomUUID();

    @Test
    void schedule_returns201_withLocation() {
        when(authentication.getName()).thenReturn(USER_ID.toString());
        var req = new ScheduleInterviewRequest(Instant.now().plusSeconds(86400),
                "https://meet.example.com", "Technical round");
        var response = buildInterviewResponse();
        when(interviewService.schedule(APP_ID, req, USER_ID)).thenReturn(response);

        var result = interviewController.schedule(APP_ID, req, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void list_returns200_withInterviews() {
        var response = buildInterviewResponse();
        when(interviewService.listByApplication(APP_ID)).thenReturn(List.of(response));

        var result = interviewController.list(APP_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    // ── FeedbackController tests ──────────────────────────────────────────────

    @InjectMocks
    FeedbackController feedbackController;

    @Test
    void submitFeedback_returns201() {
        when(authentication.getName()).thenReturn(REVIEWER_ID.toString());
        var req = new SubmitFeedbackRequest(5, "Excellent", "PASS");
        var response = buildFeedbackResponse();
        when(interviewService.submitFeedback(INTERVIEW_ID, req, REVIEWER_ID)).thenReturn(response);

        var result = feedbackController.submitFeedback(INTERVIEW_ID, req, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void listFeedback_returns200() {
        var response = buildFeedbackResponse();
        when(interviewService.listFeedback(INTERVIEW_ID)).thenReturn(List.of(response));

        var result = feedbackController.listFeedback(INTERVIEW_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    private InterviewResponse buildInterviewResponse() {
        return new InterviewResponse(INTERVIEW_ID, APP_ID, Instant.now().plusSeconds(86400),
                "https://meet.example.com", "Technical round", USER_ID, Instant.now(), List.of());
    }

    private FeedbackResponse buildFeedbackResponse() {
        return new FeedbackResponse(UUID.randomUUID(), INTERVIEW_ID, REVIEWER_ID,
                5, "Excellent", "PASS", Instant.now());
    }
}
