package com.demo.app.compliance.service;

import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID actorId, String action, String entityType, UUID entityId,
                    Map<String, Object> before, Map<String, Object> after, String outcome) {
        try {
            repository.save(AuditEvent.builder()
                    .actorId(actorId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .beforeState(before)
                    .afterState(after)
                    .outcome(outcome)
                    .build());
        } catch (Exception e) {
            log.error("Failed to save audit event: action={}, entity={}", action, entityType, e);
        }
    }
}
