package com.demo.app.recruitment.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JobPostingResponse(
        UUID id,
        String title,
        String department,
        String location,
        String description,
        String requirements,
        LocalDate deadline,
        String status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        int applicationCount
) {}
