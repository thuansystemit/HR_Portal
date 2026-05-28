package com.demo.app.compliance.service;

import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

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
        repository.save(AuditEvent.builder()
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
    }
}
