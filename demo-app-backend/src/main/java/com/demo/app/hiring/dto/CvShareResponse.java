package com.demo.app.hiring.dto;

import java.time.Instant;
import java.util.UUID;

public record CvShareResponse(
        UUID id,
        UUID hiringRequestId,
        String hiringRequestTitle,
        UUID cvCandidateId,
        String candidateFullName,
        String candidateHiringStatus,
        UUID sharedBy,
        String sharedByName,
        UUID sharedWith,
        String sharedWithName,
        String impression,
        String comment,
        Instant sharedAt,
        Instant reviewedAt
) {}
