package com.demo.app.recruitment.service;

import com.demo.app.iam.repository.RoleRepository;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.platform.notification.NotificationService;
import com.demo.app.recruitment.dto.FeedbackResponse;
import com.demo.app.recruitment.dto.InterviewResponse;
import com.demo.app.recruitment.dto.ScheduleInterviewRequest;
import com.demo.app.recruitment.dto.SubmitFeedbackRequest;
import com.demo.app.recruitment.entity.Interview;
import com.demo.app.recruitment.entity.InterviewFeedback;
import com.demo.app.recruitment.repository.InterviewFeedbackRepository;
import com.demo.app.recruitment.repository.InterviewRepository;
import com.demo.app.recruitment.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InterviewService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    private static final List<String> HR_ROLE_NAMES = List.of("HR", "Administrator");

    private final JobApplicationRepository jobApplicationRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository feedbackRepository;
    private final NotificationService notificationService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public InterviewResponse schedule(UUID appId, ScheduleInterviewRequest req, UUID createdBy) {
        if (!jobApplicationRepository.existsById(appId)) {
            throw new ResourceNotFoundException("JobApplication", appId);
        }

        var interview = Interview.builder()
                .applicationId(appId)
                .interviewerId(req.interviewerId())
                .scheduledAt(req.scheduledAt())
                .meetingLink(req.meetingLink())
                .notes(req.notes())
                .createdBy(createdBy)
                .build();
        var saved = interviewRepository.save(interview);

        if (saved.getInterviewerId() != null) {
            String dateStr = DATE_FMT.format(saved.getScheduledAt());
            String meetingInfo = saved.getMeetingLink() != null
                    ? " Meeting link: " + saved.getMeetingLink()
                    : "";
            notificationService.send(
                    saved.getInterviewerId(),
                    "Interview Scheduled",
                    "You have been assigned as an interviewer for application " + appId
                            + " on " + dateStr + "." + meetingInfo
            );
        }

        return toResponse(saved, List.of());
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> listByApplication(UUID appId) {
        return interviewRepository.findByApplicationIdOrderByScheduledAtAsc(appId).stream()
                .map(i -> {
                    var feedback = feedbackRepository.findByInterviewId(i.getId()).stream()
                            .map(this::toFeedbackResponse)
                            .toList();
                    return toResponse(i, feedback);
                })
                .toList();
    }

    public FeedbackResponse submitFeedback(UUID interviewId, SubmitFeedbackRequest req, UUID reviewerId) {
        if (!interviewRepository.existsById(interviewId)) {
            throw new ResourceNotFoundException("Interview", interviewId);
        }

        if (feedbackRepository.existsByInterviewIdAndReviewerId(interviewId, reviewerId)) {
            throw new ConflictException("Reviewer has already submitted feedback for this interview");
        }

        var feedback = InterviewFeedback.builder()
                .interviewId(interviewId)
                .reviewerId(reviewerId)
                .rating(req.rating().shortValue())
                .notes(req.notes())
                .recommendation(req.recommendation())
                .build();
        var saved = feedbackRepository.save(feedback);

        notifyHrAboutFeedback(interviewId, reviewerId, req.recommendation());

        return toFeedbackResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> listFeedback(UUID interviewId) {
        return feedbackRepository.findByInterviewId(interviewId).stream()
                .map(this::toFeedbackResponse)
                .toList();
    }

    private void notifyHrAboutFeedback(UUID interviewId, UUID reviewerId, String recommendation) {
        String body = "Feedback submitted for interview " + interviewId
                + " by reviewer " + reviewerId
                + ". Recommendation: " + recommendation;

        HR_ROLE_NAMES.forEach(roleName ->
                roleRepository.findByName(roleName).ifPresent(role ->
                        userRepository.findActiveByRoleId(role.getId()).forEach(hrUser ->
                                notificationService.send(hrUser.getId(), "Interview Feedback Submitted", body)
                        )
                )
        );
    }

    private InterviewResponse toResponse(Interview i, List<FeedbackResponse> feedback) {
        return new InterviewResponse(
                i.getId(), i.getApplicationId(), i.getInterviewerId(), i.getScheduledAt(),
                i.getMeetingLink(), i.getNotes(), i.getCreatedBy(), i.getCreatedAt(),
                feedback);
    }

    private FeedbackResponse toFeedbackResponse(InterviewFeedback f) {
        return new FeedbackResponse(
                f.getId(), f.getInterviewId(), f.getReviewerId(),
                f.getRating(), f.getNotes(), f.getRecommendation(), f.getSubmittedAt());
    }
}
