package com.demo.app.recruitment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ScheduleInterviewRequest(
        @NotNull Instant scheduledAt,
        String meetingLink,
        String notes,
        UUID interviewerId
) {}
