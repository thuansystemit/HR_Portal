package com.demo.app.recruitment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateApplicationRequest(
        @NotNull UUID cvCandidateId,
        String notes
) {}
