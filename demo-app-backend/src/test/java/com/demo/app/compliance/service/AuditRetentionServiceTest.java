package com.demo.app.compliance.service;

import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditRetentionServiceTest {

    @Mock AuditEventRepository auditEventRepository;
    @Mock EntityManager entityManager;
    @Mock Query maintenanceQuery;

    @InjectMocks
    AuditRetentionService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "retentionDays", 365);
        ReflectionTestUtils.setField(service, "archivePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    // --- archiveEvents ---

    @Test
    void archiveEvents_writesNdjsonGz_andReturnsCount() throws IOException {
        var event = buildEvent("LOGIN");
        when(auditEventRepository.streamByDateRange(eq(Instant.EPOCH), any()))
                .thenReturn(Stream.of(event));

        var cutoff = Instant.now();
        long count = service.archiveEvents(cutoff);

        assertThat(count).isEqualTo(1);
        var archives = Files.list(tempDir).toList();
        assertThat(archives).hasSize(1);
        assertThat(archives.get(0).getFileName().toString()).endsWith(".ndjson.gz");

        // Verify the file is valid gzip
        try (var gzip = new GZIPInputStream(Files.newInputStream(archives.get(0)))) {
            var content = new String(gzip.readAllBytes());
            assertThat(content).contains("LOGIN");
            assertThat(content.trim().split("\n")).hasSize(1);
        }
    }

    @Test
    void archiveEvents_multipleEvents_eachOnOwnLine() throws IOException {
        var e1 = buildEvent("LOGIN");
        var e2 = buildEvent("LOGOUT");
        when(auditEventRepository.streamByDateRange(eq(Instant.EPOCH), any()))
                .thenReturn(Stream.of(e1, e2));

        long count = service.archiveEvents(Instant.now());

        assertThat(count).isEqualTo(2);
        var file = Files.list(tempDir).findFirst().orElseThrow();
        try (var gzip = new GZIPInputStream(Files.newInputStream(file))) {
            var lines = new String(gzip.readAllBytes()).trim().split("\n");
            assertThat(lines).hasSize(2);
        }
    }

    @Test
    void archiveEvents_noEvents_deletesEmptyFileAndReturnsZero() throws IOException {
        when(auditEventRepository.streamByDateRange(eq(Instant.EPOCH), any()))
                .thenReturn(Stream.empty());

        long count = service.archiveEvents(Instant.now());

        assertThat(count).isZero();
        assertThat(Files.list(tempDir).count()).isZero();
    }

    @Test
    void archiveEvents_createsArchiveDirectory_ifAbsent() throws IOException {
        var nested = tempDir.resolve("nested/archives");
        ReflectionTestUtils.setField(service, "archivePath", nested.toString());
        when(auditEventRepository.streamByDateRange(eq(Instant.EPOCH), any()))
                .thenReturn(Stream.of(buildEvent("CREATE")));

        service.archiveEvents(Instant.now());

        assertThat(Files.isDirectory(nested)).isTrue();
        assertThat(Files.list(nested).count()).isEqualTo(1);
    }

    // --- purgeEvents ---

    @Test
    void purgeEvents_setsMaintenanceGuc_thenBulkDeletes() {
        when(entityManager.createNativeQuery("SET LOCAL app.audit_maintenance = 'true'"))
                .thenReturn(maintenanceQuery);
        when(maintenanceQuery.executeUpdate()).thenReturn(0);
        when(auditEventRepository.deleteByOccurredAtBefore(any())).thenReturn(42);

        var cutoff = Instant.now();
        int deleted = service.purgeEvents(cutoff);

        assertThat(deleted).isEqualTo(42);
        verify(entityManager).createNativeQuery("SET LOCAL app.audit_maintenance = 'true'");
        verify(maintenanceQuery).executeUpdate();

        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(auditEventRepository).deleteByOccurredAtBefore(captor.capture());
        assertThat(captor.getValue()).isEqualTo(cutoff);
    }

    @Test
    void purgeEvents_maintenanceGucSetBeforeDelete() {
        var order = inOrder(entityManager, maintenanceQuery, auditEventRepository);
        when(entityManager.createNativeQuery("SET LOCAL app.audit_maintenance = 'true'"))
                .thenReturn(maintenanceQuery);
        when(maintenanceQuery.executeUpdate()).thenReturn(0);
        when(auditEventRepository.deleteByOccurredAtBefore(any())).thenReturn(0);

        service.purgeEvents(Instant.now());

        order.verify(entityManager).createNativeQuery("SET LOCAL app.audit_maintenance = 'true'");
        order.verify(maintenanceQuery).executeUpdate();
        order.verify(auditEventRepository).deleteByOccurredAtBefore(any());
    }

    // --- runRetentionJob integration ---

    @Test
    void runRetentionJob_aborts_whenArchiveFails() throws IOException {
        when(auditEventRepository.streamByDateRange(eq(Instant.EPOCH), any()))
                .thenThrow(new RuntimeException("DB error"));

        assertThatCode(() -> service.runRetentionJob()).doesNotThrowAnyException();
        verify(auditEventRepository, never()).deleteByOccurredAtBefore(any());
    }

    @Test
    void runRetentionJob_skipsDelete_whenNoEventsArchived() throws IOException {
        when(auditEventRepository.streamByDateRange(eq(Instant.EPOCH), any()))
                .thenReturn(Stream.empty());

        service.runRetentionJob();

        verify(auditEventRepository, never()).deleteByOccurredAtBefore(any());
        verify(entityManager, never()).createNativeQuery(any());
    }

    private AuditEvent buildEvent(String action) {
        return AuditEvent.builder()
                .id(UUID.randomUUID())
                .action(action)
                .entityType("User")
                .outcome("success")
                .occurredAt(Instant.now().minusSeconds(400 * 86_400L))
                .build();
    }
}
