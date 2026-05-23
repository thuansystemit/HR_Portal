package com.demo.app.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record IngestInvoiceRequest(
        @NotNull UUID documentId,
        @NotNull UUID documentCategoryId,
        @NotBlank @Pattern(regexp = "^[\\w\\-]+\\.json$", message = "jsonFile must be a plain filename ending in .json") String jsonFile
) {}
