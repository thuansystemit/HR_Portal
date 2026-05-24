package com.demo.app.recruitment.dto;

import java.time.Instant;
import java.util.UUID;

public record StageHistoryEntry(
        UUID id,
        String fromStage,
        String toStage,
        UUID movedBy,
        String notes,
        Instant movedAt
) {}
