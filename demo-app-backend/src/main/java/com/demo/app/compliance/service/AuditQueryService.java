package com.demo.app.compliance.service;

import com.demo.app.compliance.dto.AuditEventResponse;
import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import com.demo.app.iam.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    static final int MAX_PAGE_SIZE = 200;

    private final AuditEventRepository auditEventRepository;

    @Transactional(readOnly = true)
    public PagedResponse<AuditEventResponse> search(
            Instant from, Instant to, String action, UUID actorId, String entityType,
            int page, int size) {
        var clampedSize = Math.min(size, MAX_PAGE_SIZE);
        var pageable = PageRequest.of(page, clampedSize);
        var result = auditEventRepository.search(from, to, action, actorId, entityType, pageable);
        var content = result.getContent().stream().map(this::toResponse).toList();
        return new PagedResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(
                e.getId(), e.getActorId(), e.getAction(), e.getEntityType(),
                e.getEntityId(), e.getBeforeState(), e.getAfterState(),
                e.getIpAddress(), e.getOutcome(), e.getCorrelationId(),
                e.getSessionId(), e.getOccurredAt());
    }
}
