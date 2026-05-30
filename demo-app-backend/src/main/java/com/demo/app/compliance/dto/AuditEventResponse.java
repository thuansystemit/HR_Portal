package com.demo.app.compliance.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        String ipAddress,
        String outcome,
        String correlationId,
        String sessionId,
        Instant occurredAt
) {}
