package com.demo.app.recruitment.dto;

import java.time.LocalDate;

public record UpdateJobPostingRequest(
        String title,
        String department,
        String location,
        String description,
        String requirements,
        LocalDate deadline,
        String status
) {}
