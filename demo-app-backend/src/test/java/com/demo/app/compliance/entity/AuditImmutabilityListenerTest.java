package com.demo.app.compliance.entity;

import com.demo.app.compliance.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

class AuditImmutabilityListenerTest {

    private final AuditImmutabilityListener listener = new AuditImmutabilityListener();
    private final AuditEventRepository repo =
            Mockito.mock(AuditEventRepository.class, CALLS_REAL_METHODS);

    private AuditEvent buildEvent() {
        return AuditEvent.builder()
                .id(UUID.randomUUID())
                .actorId(UUID.randomUUID())
                .action("USER_LOGIN")
                .entityType("User")
                .entityId(UUID.randomUUID())
                .outcome("success")
                .occurredAt(Instant.now())
                .build();
    }

    // --- Listener tests ---

    @Test
    void preRemove_throwsUnsupportedOperation() {
        var event = buildEvent();
        assertThatThrownBy(() -> listener.onPreRemove(event))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AU-9");
    }

    @Test
    void preUpdate_throwsUnsupportedOperation() {
        var event = buildEvent();
        assertThatThrownBy(() -> listener.onPreUpdate(event))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AU-9");
    }

    // --- Repository guard tests ---

    @Test
    void repository_deleteById_throws() {
        assertThatThrownBy(() -> repo.deleteById(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AU-9");
    }

    @Test
    void repository_deleteEntity_throws() {
        assertThatThrownBy(() -> repo.delete(buildEvent()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AU-9");
    }

    @Test
    void repository_deleteAllById_throws() {
        assertThatThrownBy(() -> repo.deleteAllById(List.of(UUID.randomUUID())))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AU-9");
    }

    @Test
    void repository_deleteAllEntities_throws() {
        assertThatThrownBy(() -> repo.deleteAll(List.of(buildEvent())))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AU-9");
    }

    @Test
    void repository_deleteAll_throws() {
        assertThatThrownBy(() -> repo.deleteAll())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("AU-9");
    }
}
