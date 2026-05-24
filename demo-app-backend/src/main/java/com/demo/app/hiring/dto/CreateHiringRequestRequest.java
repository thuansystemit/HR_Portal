package com.demo.app.hiring.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateHiringRequestRequest(
        @NotBlank String title,
        String description,
        @NotBlank String roleType,
        @NotBlank String department,
        String urgency
) {}
