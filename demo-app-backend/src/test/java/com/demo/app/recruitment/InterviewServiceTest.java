package com.demo.app.recruitment;

import com.demo.app.iam.entity.Role;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.repository.RoleRepository;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.platform.notification.Notification;
import com.demo.app.platform.notification.NotificationService;
import com.demo.app.recruitment.dto.ScheduleInterviewRequest;
import com.demo.app.recruitment.dto.SubmitFeedbackRequest;
import com.demo.app.recruitment.entity.Interview;
import com.demo.app.recruitment.entity.InterviewFeedback;
import com.demo.app.recruitment.repository.InterviewFeedbackRepository;
import com.demo.app.recruitment.repository.InterviewRepository;
import com.demo.app.recruitment.repository.JobApplicationRepository;
import com.demo.app.recruitment.service.InterviewService;
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
class InterviewServiceTest {

    @Mock JobApplicationRepository jobApplicationRepository;
    @Mock InterviewRepository interviewRepository;
    @Mock InterviewFeedbackRepository feedbackRepository;
    @Mock NotificationService notificationService;
    @Mock RoleRepository roleRepository;
    @Mock UserRepository userRepository;

    @InjectMocks
    InterviewService interviewService;

    private final UUID APP_ID        = UUID.randomUUID();
    private final UUID INTERVIEW_ID  = UUID.randomUUID();
    private final UUID REVIEWER_ID   = UUID.randomUUID();
    private final UUID CREATED_BY    = UUID.randomUUID();
    private final UUID INTERVIEWER_ID = UUID.randomUUID();

    // ── schedule ──────────────────────────────────────────────────────────────

    @Test
    void schedule_success_savesInterview() {
        var req = new ScheduleInterviewRequest(Instant.now().plusSeconds(86400),
                "https://meet.example.com/abc", "First round", null);
        var saved = buildInterview(null);

        when(jobApplicationRepository.existsById(APP_ID)).thenReturn(true);
        when(interviewRepository.save(any())).thenReturn(saved);

        var result = interviewService.schedule(APP_ID, req, CREATED_BY);

        assertThat(result.id()).isEqualTo(INTERVIEW_ID);
        assertThat(result.applicationId()).isEqualTo(APP_ID);
        assertThat(result.feedback()).isEmpty();
        verify(interviewRepository).save(any());
        verify(notificationService, never()).send(any(), any(), any());
    }

    @Test
    void schedule_withInterviewerId_sendsNotification() {
        var req = new ScheduleInterviewRequest(Instant.now().plusSeconds(86400),
                "https://meet.example.com/room", "Technical round", INTERVIEWER_ID);
        var saved = buildInterview(INTERVIEWER_ID);

        when(jobApplicationRepository.existsById(APP_ID)).thenReturn(true);
        when(interviewRepository.save(any())).thenReturn(saved);
        when(notificationService.send(eq(INTERVIEWER_ID), any(), any()))
                .thenReturn(new Notification());

        var result = interviewService.schedule(APP_ID, req, CREATED_BY);

        assertThat(result.interviewerId()).isEqualTo(INTERVIEWER_ID);
        verify(notificationService).send(eq(INTERVIEWER_ID), eq("Interview Scheduled"), anyString());
    }

    @Test
    void schedule_throws_whenApplicationNotFound() {
        when(jobApplicationRepository.existsById(APP_ID)).thenReturn(false);

        assertThatThrownBy(() -> interviewService.schedule(APP_ID,
                new ScheduleInterviewRequest(Instant.now(), null, null, null), CREATED_BY))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(APP_ID.toString());

        verify(interviewRepository, never()).save(any());
    }

    // ── listByApplication ─────────────────────────────────────────────────────

    @Test
    void listByApplication_returnsInterviewsWithFeedback() {
        var interview = buildInterview(null);
        var feedback = buildFeedback();

        when(interviewRepository.findByApplicationIdOrderByScheduledAtAsc(APP_ID))
                .thenReturn(List.of(interview));
        when(feedbackRepository.findByInterviewId(INTERVIEW_ID)).thenReturn(List.of(feedback));

        var result = interviewService.listByApplication(APP_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(INTERVIEW_ID);
        assertThat(result.get(0).feedback()).hasSize(1);
        assertThat(result.get(0).feedback().get(0).recommendation()).isEqualTo("PASS");
    }

    @Test
    void listByApplication_returnsEmpty_whenNoInterviews() {
        when(interviewRepository.findByApplicationIdOrderByScheduledAtAsc(APP_ID))
                .thenReturn(List.of());

        var result = interviewService.listByApplication(APP_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void listByApplication_returnsInterviewWithEmptyFeedback() {
        var interview = buildInterview(null);

        when(interviewRepository.findByApplicationIdOrderByScheduledAtAsc(APP_ID))
                .thenReturn(List.of(interview));
        when(feedbackRepository.findByInterviewId(INTERVIEW_ID)).thenReturn(List.of());

        var result = interviewService.listByApplication(APP_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).feedback()).isEmpty();
    }

    // ── submitFeedback ────────────────────────────────────────────────────────

    @Test
    void submitFeedback_success_savesFeedbackAndNotifiesHr() {
        var req = new SubmitFeedbackRequest(4, "Great candidate", "PASS");
        var saved = buildFeedback();
        var hrRole = Role.builder().id(UUID.randomUUID()).name("HR").build();
        var hrUser = User.builder().id(UUID.randomUUID()).fullName("HR Manager").email("hr@example.com").build();

        when(interviewRepository.existsById(INTERVIEW_ID)).thenReturn(true);
        when(feedbackRepository.existsByInterviewIdAndReviewerId(INTERVIEW_ID, REVIEWER_ID))
                .thenReturn(false);
        when(feedbackRepository.save(any())).thenReturn(saved);
        when(roleRepository.findByName("HR")).thenReturn(Optional.of(hrRole));
        when(roleRepository.findByName("Administrator")).thenReturn(Optional.empty());
        when(userRepository.findActiveByRoleId(hrRole.getId())).thenReturn(List.of(hrUser));
        when(notificationService.send(any(), any(), any())).thenReturn(new Notification());

        var result = interviewService.submitFeedback(INTERVIEW_ID, req, REVIEWER_ID);

        assertThat(result.interviewId()).isEqualTo(INTERVIEW_ID);
        assertThat(result.recommendation()).isEqualTo("PASS");
        verify(feedbackRepository).save(any());
        verify(notificationService).send(eq(hrUser.getId()), eq("Interview Feedback Submitted"), anyString());
    }

    @Test
    void submitFeedback_throws_whenInterviewNotFound() {
        when(interviewRepository.existsById(INTERVIEW_ID)).thenReturn(false);

        assertThatThrownBy(() -> interviewService.submitFeedback(INTERVIEW_ID,
                new SubmitFeedbackRequest(3, null, "HOLD"), REVIEWER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(INTERVIEW_ID.toString());

        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitFeedback_throws_whenDuplicateFeedback() {
        when(interviewRepository.existsById(INTERVIEW_ID)).thenReturn(true);
        when(feedbackRepository.existsByInterviewIdAndReviewerId(INTERVIEW_ID, REVIEWER_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> interviewService.submitFeedback(INTERVIEW_ID,
                new SubmitFeedbackRequest(5, "Excellent", "PASS"), REVIEWER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already submitted");

        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitFeedback_notifiesMultipleHrRoles() {
        var req = new SubmitFeedbackRequest(5, "Excellent", "PASS");
        var saved = buildFeedback();
        var hrRole = Role.builder().id(UUID.randomUUID()).name("HR").build();
        var adminRole = Role.builder().id(UUID.randomUUID()).name("Administrator").build();
        var hrUser = User.builder().id(UUID.randomUUID()).fullName("HR Manager").email("hr@example.com").build();
        var adminUser = User.builder().id(UUID.randomUUID()).fullName("Admin").email("admin@example.com").build();

        when(interviewRepository.existsById(INTERVIEW_ID)).thenReturn(true);
        when(feedbackRepository.existsByInterviewIdAndReviewerId(INTERVIEW_ID, REVIEWER_ID)).thenReturn(false);
        when(feedbackRepository.save(any())).thenReturn(saved);
        when(roleRepository.findByName("HR")).thenReturn(Optional.of(hrRole));
        when(roleRepository.findByName("Administrator")).thenReturn(Optional.of(adminRole));
        when(userRepository.findActiveByRoleId(hrRole.getId())).thenReturn(List.of(hrUser));
        when(userRepository.findActiveByRoleId(adminRole.getId())).thenReturn(List.of(adminUser));
        when(notificationService.send(any(), any(), any())).thenReturn(new Notification());

        interviewService.submitFeedback(INTERVIEW_ID, req, REVIEWER_ID);

        verify(notificationService).send(eq(hrUser.getId()), any(), any());
        verify(notificationService).send(eq(adminUser.getId()), any(), any());
    }

    // ── listFeedback ──────────────────────────────────────────────────────────

    @Test
    void listFeedback_returnsFeedbackForInterview() {
        var feedback = buildFeedback();
        when(feedbackRepository.findByInterviewId(INTERVIEW_ID)).thenReturn(List.of(feedback));

        var result = interviewService.listFeedback(INTERVIEW_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rating()).isEqualTo(4);
    }

    @Test
    void listFeedback_returnsEmpty_whenNoFeedback() {
        when(feedbackRepository.findByInterviewId(INTERVIEW_ID)).thenReturn(List.of());

        var result = interviewService.listFeedback(INTERVIEW_ID);

        assertThat(result).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Interview buildInterview(UUID interviewerId) {
        return Interview.builder()
                .id(INTERVIEW_ID)
                .applicationId(APP_ID)
                .interviewerId(interviewerId)
                .scheduledAt(Instant.now().plusSeconds(86400))
                .meetingLink("https://meet.example.com/abc")
                .notes("First round")
                .createdBy(CREATED_BY)
                .createdAt(Instant.now())
                .build();
    }

    private InterviewFeedback buildFeedback() {
        return InterviewFeedback.builder()
                .id(UUID.randomUUID())
                .interviewId(INTERVIEW_ID)
                .reviewerId(REVIEWER_ID)
                .rating((short) 4)
                .notes("Good performance")
                .recommendation("PASS")
                .submittedAt(Instant.now())
                .build();
    }
}
