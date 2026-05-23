package com.demo.app.cv.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record IngestCvRequest(
        @NotNull UUID documentId,
        @NotNull UUID documentCategoryId,
        @Pattern(regexp = "^[\\w\\-]+\\.json$", message = "jsonFile must be a plain filename ending in .json") String jsonFile,
        String extractionStatus,
        List<String> guardrailWarnings
) {}
