package com.demo.app.recruitment.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InterviewResponse(
        UUID id,
        UUID applicationId,
        UUID interviewerId,
        Instant scheduledAt,
        String meetingLink,
        String notes,
        UUID createdBy,
        Instant createdAt,
        List<FeedbackResponse> feedback
) {}
