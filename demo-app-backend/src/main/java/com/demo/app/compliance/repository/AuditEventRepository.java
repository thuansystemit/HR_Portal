package com.demo.app.compliance.repository;

import com.demo.app.compliance.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByActorIdOrderByOccurredAtDesc(UUID actorId, Pageable pageable);
}
