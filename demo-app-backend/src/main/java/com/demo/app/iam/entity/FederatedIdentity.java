package com.demo.app.iam.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * IA-2(12) / IA-8(2): links an external SAML identity (provider + NameID) to a
 * local User account. Used by SamlUserProvisioningService for JIT provisioning.
 * The unique constraint on (provider, name_id) ensures a single IdP identity maps
 * to exactly one local user.
 */
@Entity
@Table(
    name = "federated_identities",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_federated_identities_provider_nameid",
        columnNames = {"provider", "name_id"}
    )
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FederatedIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    /** IdP registration ID / provider key, e.g. "logingov". */
    @Column(nullable = false, length = 100)
    private String provider;

    /** SAML NameID value — PIV/CAC DN or login.gov persistent identifier. */
    @Column(nullable = false, length = 512)
    private String nameId;

    /** SAML NameID format URI (may be null for unspecified format). */
    @Column(length = 200)
    private String nameIdFormat;

    /** Raw SAML assertion attributes retained for audit and re-provisioning. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> attributes;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.lastSeenAt = Instant.now();
    }
}
