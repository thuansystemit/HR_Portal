package com.demo.app.hiring.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitImpressionRequest(
        @NotBlank String impression,
        String comment
) {}
