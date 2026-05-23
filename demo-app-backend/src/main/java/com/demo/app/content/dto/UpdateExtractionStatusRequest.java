package com.demo.app.content.dto;

import com.demo.app.content.entity.ExtractionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateExtractionStatusRequest(
        @NotNull ExtractionStatus status,
        String errorPhase,
        String errorMessage
) {}
