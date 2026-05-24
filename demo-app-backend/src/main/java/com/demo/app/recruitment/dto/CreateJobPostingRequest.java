package com.demo.app.recruitment.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateJobPostingRequest(
        @NotBlank String title,
        String department,
        String location,
        String description,
        String requirements,
        LocalDate deadline,
        String status
) {}
