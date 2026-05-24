package com.demo.app.hiring.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ShareCvRequest(
        @NotNull UUID cvCandidateId,
        @NotNull UUID sharedWith,
        String comment
) {}
