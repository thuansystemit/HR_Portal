package com.demo.app.recruitment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitFeedbackRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        String notes,
        @NotBlank String recommendation
) {}
