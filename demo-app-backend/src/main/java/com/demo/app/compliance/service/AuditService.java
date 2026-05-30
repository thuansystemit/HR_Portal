package com.demo.app.compliance.service;

import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    // AU-6/SI-4: dedicated logger routed to AUDIT_FILE appender (NDJSON) for SIEM ingestion
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");

    private final AuditEventRepository repository;

    /**
     * Write an audit record synchronously in a new transaction (AU-5).
     * Using REQUIRES_NEW so this commits even if the caller's transaction rolls back.
     * Synchronous — if this fails, the exception propagates and the business operation fails too,
     * ensuring no unaudited state changes (AU-5: response to audit failures).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID actorId, String action, String entityType, UUID entityId,
                    Map<String, Object> before, Map<String, Object> after, String outcome) {
        var event = repository.save(AuditEvent.builder()
                .actorId(actorId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .beforeState(before)
                .afterState(after)
                .outcome(outcome)
                .correlationId(MDC.get("requestId"))
                .sessionId(MDC.get("sessionId"))
                .ipAddress(MDC.get("clientIp"))
                .build());

        // AU-6/SI-4: emit structured JSON line to AUDIT logger for SIEM ingestion
        AUDIT_LOG.info("audit_event",
                kv("event_id",       event.getId()),
                kv("action",         action),
                kv("actor_id",       actorId),
                kv("entity_type",    entityType),
                kv("entity_id",      entityId),
                kv("outcome",        outcome),
                kv("correlation_id", event.getCorrelationId()),
                kv("session_id",     event.getSessionId()),
                kv("client_ip",      event.getIpAddress()),
                kv("occurred_at",    event.getOccurredAt()));
    }
}
