package com.demo.app.recruitment.dto;

import java.time.LocalDate;
import java.util.UUID;

public record JobPostingSummary(
        UUID id,
        String title,
        String department,
        String location,
        String status,
        LocalDate deadline,
        int applicationCount
) {}
