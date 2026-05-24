package com.demo.app.hiring.dto;

import java.time.Instant;
import java.util.UUID;

public record HiringRequestResponse(
        UUID id,
        UUID requesterId,
        String requesterName,
        String title,
        String description,
        String roleType,
        String department,
        String urgency,
        String status,
        UUID jobPostingId,
        Instant createdAt,
        Instant updatedAt
) {}
