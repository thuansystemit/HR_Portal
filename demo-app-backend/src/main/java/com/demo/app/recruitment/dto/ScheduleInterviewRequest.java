package com.demo.app.recruitment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ScheduleInterviewRequest(
        @NotNull Instant scheduledAt,
        String meetingLink,
        String notes
) {}
