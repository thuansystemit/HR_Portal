package com.demo.app.compliance.service;

import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditEventRepository repository;

    @InjectMocks
    AuditService auditService;

    private final UUID ACTOR_ID = UUID.randomUUID();
    private final UUID ENTITY_ID = UUID.randomUUID();

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
    void log_doesNotThrow_whenRepositoryFails() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatCode(() -> auditService.log(ACTOR_ID, "USER_LOGIN", "User", ENTITY_ID,
                null, null, "success"))
                .doesNotThrowAnyException();
    }
}
