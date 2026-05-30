package com.demo.app.compliance.repository;

import com.demo.app.compliance.entity.AuditEvent;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    // AU-9: audit events are append-only — all delete operations are blocked at the Spring Data layer
    @Override
    default void deleteById(UUID id) {
        throw new UnsupportedOperationException("Audit events are immutable — DELETE is not permitted (AU-9)");
    }

    @Override
    default void delete(AuditEvent entity) {
        throw new UnsupportedOperationException("Audit events are immutable — DELETE is not permitted (AU-9)");
    }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) {
        throw new UnsupportedOperationException("Audit events are immutable — DELETE is not permitted (AU-9)");
    }

    @Override
    default void deleteAll(Iterable<? extends AuditEvent> entities) {
        throw new UnsupportedOperationException("Audit events are immutable — DELETE is not permitted (AU-9)");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("Audit events are immutable — DELETE is not permitted (AU-9)");
    }

    Page<AuditEvent> findByActorIdOrderByOccurredAtDesc(UUID actorId, Pageable pageable);

    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "500"))
    @Query("SELECT e FROM AuditEvent e WHERE e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC")
    Stream<AuditEvent> streamByDateRange(@Param("from") Instant from, @Param("to") Instant to);

    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "500"))
    @Query("SELECT e FROM AuditEvent e WHERE e.occurredAt >= :from AND e.occurredAt <= :to AND e.action = :action ORDER BY e.occurredAt ASC")
    Stream<AuditEvent> streamByDateRangeAndAction(@Param("from") Instant from, @Param("to") Instant to, @Param("action") String action);

    // AU-6: flexible paginated search for audit review and analysis UI
    @Query("SELECT e FROM AuditEvent e WHERE " +
           "(:from IS NULL OR e.occurredAt >= :from) AND " +
           "(:to IS NULL OR e.occurredAt <= :to) AND " +
           "(:action IS NULL OR e.action = :action) AND " +
           "(:actorId IS NULL OR e.actorId = :actorId) AND " +
           "(:entityType IS NULL OR e.entityType = :entityType) " +
           "ORDER BY e.occurredAt DESC")
    Page<AuditEvent> search(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("action") String action,
            @Param("actorId") UUID actorId,
            @Param("entityType") String entityType,
            Pageable pageable);

    // IR-5: count recent events of a given type — used by anomaly detector to check threshold breaches
    @Query("SELECT COUNT(e) FROM AuditEvent e WHERE e.action = :action AND e.occurredAt >= :since")
    long countByActionSince(@Param("action") String action, @Param("since") Instant since);

    // AU-11: used only by AuditRetentionService inside a maintenance transaction (SET LOCAL app.audit_maintenance = 'true')
    @Modifying
    @Query("DELETE FROM AuditEvent e WHERE e.occurredAt < :cutoff")
    int deleteByOccurredAtBefore(@Param("cutoff") Instant cutoff);
}
