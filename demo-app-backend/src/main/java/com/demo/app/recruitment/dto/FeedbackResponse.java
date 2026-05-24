package com.demo.app.recruitment.dto;

import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        UUID interviewId,
        UUID reviewerId,
        int rating,
        String notes,
        String recommendation,
        Instant submittedAt
) {}
