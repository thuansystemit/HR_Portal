package com.demo.app.recruitment.dto;

import jakarta.validation.constraints.NotBlank;

public record MoveStageRequest(
        @NotBlank String stage,
        String notes
) {}
