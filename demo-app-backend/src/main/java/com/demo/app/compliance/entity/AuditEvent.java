package com.demo.app.compliance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    private UUID actorId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 60)
    private String entityType;

    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> afterState;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @Column(nullable = false, length = 20)
    private String outcome = "success";

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();
}
