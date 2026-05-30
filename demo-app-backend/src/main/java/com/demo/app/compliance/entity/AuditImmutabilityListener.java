package com.demo.app.compliance.entity;

import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;

/**
 * AU-9: Audit events must not be deleted or modified after creation.
 * This listener throws at the JPA lifecycle layer — before any SQL reaches the DB.
 * A matching DB trigger in V30 provides a second layer of protection.
 */
public class AuditImmutabilityListener {

    @PreRemove
    void onPreRemove(AuditEvent event) {
        throw new UnsupportedOperationException(
                "Audit events are immutable — DELETE is not permitted (AU-9)");
    }

    @PreUpdate
    void onPreUpdate(AuditEvent event) {
        throw new UnsupportedOperationException(
                "Audit events are immutable — UPDATE is not permitted (AU-9)");
    }
}
