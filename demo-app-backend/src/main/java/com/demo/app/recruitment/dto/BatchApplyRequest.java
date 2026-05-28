package com.demo.app.recruitment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BatchApplyRequest(
        @NotNull @Size(min = 1, max = 50) List<@NotNull UUID> cvCandidateIds,
        String notes
) {}
