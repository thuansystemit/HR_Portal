package com.demo.app.compliance.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditEventRepository repository;

    @InjectMocks
    AuditService auditService;

    private final UUID ACTOR_ID  = UUID.randomUUID();
    private final UUID ENTITY_ID = UUID.randomUUID();

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void attachAppender() {
        listAppender = new ListAppender<>();
        listAppender.start();
        auditLogger().addAppender(listAppender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger().detachAppender(listAppender);
    }

    @Test
    void log_savesAuditEvent() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        auditService.log(ACTOR_ID, "USER_LOGIN", "User", ENTITY_ID,
                null, Map.of("status", "active"), "success");

        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(ACTOR_ID);
        assertThat(saved.getAction()).isEqualTo("USER_LOGIN");
        assertThat(saved.getEntityType()).isEqualTo("User");
        assertThat(saved.getEntityId()).isEqualTo(ENTITY_ID);
        assertThat(saved.getOutcome()).isEqualTo("success");
    }

    @Test
    void log_emitsStructuredAuditLogLine_afterDbSave() {
        var savedEvent = AuditEvent.builder()
                .id(UUID.randomUUID())
                .actorId(ACTOR_ID)
                .action("USER_CREATED")
                .entityType("User")
                .entityId(ENTITY_ID)
                .outcome("success")
                .occurredAt(Instant.now())
                .build();
        when(repository.save(any())).thenReturn(savedEvent);

        auditService.log(ACTOR_ID, "USER_CREATED", "User", ENTITY_ID, null, null, "success");

        assertThat(listAppender.list).hasSize(1);
        var event = listAppender.list.get(0);
        assertThat(event.getMessage()).isEqualTo("audit_event");

        // Verify StructuredArguments key-value pairs are present
        var args = event.getArgumentArray();
        assertThat(args).isNotNull();
        String argsStr = java.util.Arrays.toString(args);
        assertThat(argsStr).contains("USER_CREATED");
        assertThat(argsStr).contains("success");
        assertThat(argsStr).contains("User");
    }

    @Test
    void log_structuredLog_containsActorAndEntityIds() {
        var eventId = UUID.randomUUID();
        var savedEvent = AuditEvent.builder()
                .id(eventId)
                .actorId(ACTOR_ID)
                .action("DOCUMENT_UPLOADED")
                .entityType("Document")
                .entityId(ENTITY_ID)
                .outcome("success")
                .occurredAt(Instant.now())
                .build();
        when(repository.save(any())).thenReturn(savedEvent);

        auditService.log(ACTOR_ID, "DOCUMENT_UPLOADED", "Document", ENTITY_ID, null, null, "success");

        assertThat(listAppender.list).hasSize(1);
        var args = listAppender.list.get(0).getArgumentArray();
        String argsStr = java.util.Arrays.toString(args);
        assertThat(argsStr).contains(ACTOR_ID.toString());
        assertThat(argsStr).contains(ENTITY_ID.toString());
        assertThat(argsStr).contains(eventId.toString());
    }

    @Test
    void log_noStructuredLog_whenRepositoryFails() {
        // AU-5: audit failure propagates — no log line when DB save fails
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> auditService.log(ACTOR_ID, "USER_LOGIN", "User", ENTITY_ID,
                null, null, "success"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error");

        assertThat(listAppender.list).isEmpty();
    }

    @Test
    void log_propagatesException_whenRepositoryFails() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> auditService.log(ACTOR_ID, "USER_LOGIN", "User", ENTITY_ID,
                null, null, "success"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error");
    }

    private static Logger auditLogger() {
        return (Logger) LoggerFactory.getLogger("AUDIT");
    }
}
