package com.demo.app.compliance.repository;

import com.demo.app.compliance.entity.AuditEvent;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByActorIdOrderByOccurredAtDesc(UUID actorId, Pageable pageable);

    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "500"))
    @Query("SELECT e FROM AuditEvent e WHERE e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC")
    Stream<AuditEvent> streamByDateRange(@Param("from") Instant from, @Param("to") Instant to);

    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "500"))
    @Query("SELECT e FROM AuditEvent e WHERE e.occurredAt >= :from AND e.occurredAt <= :to AND e.action = :action ORDER BY e.occurredAt ASC")
    Stream<AuditEvent> streamByDateRangeAndAction(@Param("from") Instant from, @Param("to") Instant to, @Param("action") String action);
}
