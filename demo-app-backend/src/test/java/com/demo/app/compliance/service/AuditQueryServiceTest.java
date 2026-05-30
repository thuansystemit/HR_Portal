package com.demo.app.compliance.service;

import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock AuditEventRepository auditEventRepository;

    AuditQueryService service;

    @BeforeEach
    void setUp() {
        service = new AuditQueryService(auditEventRepository);
    }

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

    @Test
    void search_returnsPagedResponse_mappedFromPage() {
        var event = buildEvent();
        var pageResult = new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1);
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(pageResult);

        var result = service.search(null, null, null, null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void search_mapsAllEventFieldsToResponse() {
        var event = buildEvent();
        var pageResult = new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1);
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(pageResult);

        var dto = service.search(null, null, null, null, null, 0, 20).content().get(0);

        assertThat(dto.id()).isEqualTo(event.getId());
        assertThat(dto.actorId()).isEqualTo(event.getActorId());
        assertThat(dto.action()).isEqualTo(event.getAction());
        assertThat(dto.entityType()).isEqualTo(event.getEntityType());
        assertThat(dto.entityId()).isEqualTo(event.getEntityId());
        assertThat(dto.outcome()).isEqualTo(event.getOutcome());
        assertThat(dto.occurredAt()).isEqualTo(event.getOccurredAt());
    }

    private PageImpl<AuditEvent> emptyPage(int pageSize) {
        return new PageImpl<>(List.<AuditEvent>of(), PageRequest.of(0, pageSize), 0);
    }

    @Test
    void search_clampsPageSizeToMax() {
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage(AuditQueryService.MAX_PAGE_SIZE));

        var captor = ArgumentCaptor.forClass(Pageable.class);
        service.search(null, null, null, null, null, 0, 9999);

        verify(auditEventRepository).search(any(), any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(AuditQueryService.MAX_PAGE_SIZE);
    }

    @Test
    void search_passesAllFiltersToRepository() {
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-01-31T23:59:59Z");
        var actorId = UUID.randomUUID();
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage(20));

        service.search(from, to, "USER_LOGIN", actorId, "User", 0, 20);

        verify(auditEventRepository).search(
                eq(from), eq(to), eq("USER_LOGIN"), eq(actorId), eq("User"), any());
    }

    @Test
    void search_passesNullFilters_whenNotProvided() {
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage(20));

        service.search(null, null, null, null, null, 0, 20);

        verify(auditEventRepository).search(isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void search_returnsEmptyContent_whenNoResults() {
        when(auditEventRepository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage(20));

        var result = service.search(null, null, null, null, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
    }
}
