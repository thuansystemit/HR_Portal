package com.demo.app.recruitment.dto;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID jobPostingId,
        String jobTitle,
        UUID cvCandidateId,
        UUID documentCategoryId,
        String candidateFullName,
        String candidateEmail,
        String stage,
        String notes,
        Instant appliedAt,
        Instant updatedAt
) {}
