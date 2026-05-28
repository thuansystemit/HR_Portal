package com.demo.app.compliance.service;

import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditExportServiceTest {

    @Mock AuditEventRepository repository;
    @InjectMocks AuditExportService service;

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO   = Instant.parse("2026-01-31T23:59:59Z");

    private AuditEvent event(String action, String outcome) {
        return AuditEvent.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .actorId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .action(action)
                .entityType("User")
                .entityId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
                .outcome(outcome)
                .ipAddress("10.0.0.1")
                .correlationId("corr-1")
                .sessionId("sess-1")
                .occurredAt(Instant.parse("2026-01-15T10:00:00Z"))
                .build();
    }

    @Test
    void exportCsv_writesHeader() throws IOException {
        when(repository.streamByDateRange(FROM, TO)).thenReturn(Stream.empty());
        var out = new ByteArrayOutputStream();

        service.exportCsv(FROM, TO, null, out);

        assertThat(out.toString()).startsWith(AuditExportService.CSV_HEADER);
    }

    @Test
    void exportCsv_writesRowPerEvent() throws IOException {
        when(repository.streamByDateRange(FROM, TO)).thenReturn(Stream.of(event("USER_LOGIN", "success")));
        var out = new ByteArrayOutputStream();

        service.exportCsv(FROM, TO, null, out);

        String csv = out.toString();
        assertThat(csv).contains("USER_LOGIN");
        assertThat(csv).contains("10.0.0.1");
        assertThat(csv).contains("corr-1");
        assertThat(csv).contains("sess-1");
        assertThat(csv).contains("2026-01-15T10:00:00Z");
    }

    @Test
    void exportCsv_usesActionFilter_whenProvided() throws IOException {
        when(repository.streamByDateRangeAndAction(FROM, TO, "USER_LOGIN")).thenReturn(Stream.empty());
        var out = new ByteArrayOutputStream();

        service.exportCsv(FROM, TO, "USER_LOGIN", out);

        verify(repository).streamByDateRangeAndAction(FROM, TO, "USER_LOGIN");
        verify(repository, never()).streamByDateRange(any(), any());
    }

    @Test
    void exportCsv_usesNoActionFilter_whenBlank() throws IOException {
        when(repository.streamByDateRange(FROM, TO)).thenReturn(Stream.empty());
        var out = new ByteArrayOutputStream();

        service.exportCsv(FROM, TO, "  ", out);

        verify(repository).streamByDateRange(FROM, TO);
        verify(repository, never()).streamByDateRangeAndAction(any(), any(), any());
    }

    @Test
    void exportCsv_usesNoActionFilter_whenNull() throws IOException {
        when(repository.streamByDateRange(FROM, TO)).thenReturn(Stream.empty());
        var out = new ByteArrayOutputStream();

        service.exportCsv(FROM, TO, null, out);

        verify(repository).streamByDateRange(FROM, TO);
    }

    @Test
    void exportCsv_handlesNullFields() throws IOException {
        var e = AuditEvent.builder()
                .id(UUID.randomUUID())
                .action("USER_LOGIN")
                .entityType("User")
                .outcome("success")
                .occurredAt(Instant.now())
                .build();
        when(repository.streamByDateRange(FROM, TO)).thenReturn(Stream.of(e));
        var out = new ByteArrayOutputStream();

        service.exportCsv(FROM, TO, null, out);

        String csv = out.toString();
        assertThat(csv).contains("USER_LOGIN");
    }

    @Test
    void exportCsv_escapesCommasInFields() {
        assertThat(service.csv("hello,world")).isEqualTo("\"hello,world\"");
    }

    @Test
    void exportCsv_escapesQuotesInFields() {
        assertThat(service.csv("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    }

    @Test
    void exportCsv_escapesNewlinesInFields() {
        assertThat(service.csv("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void exportCsv_returnsEmptyString_forNullValue() {
        assertThat(service.csv(null)).isEmpty();
    }

    @Test
    void exportCsv_returnsPlainString_forSafeValue() {
        assertThat(service.csv("USER_LOGIN")).isEqualTo("USER_LOGIN");
    }

    @Test
    void exportCsv_writesMultipleRows() throws IOException {
        when(repository.streamByDateRange(FROM, TO)).thenReturn(
                Stream.of(event("USER_LOGIN", "success"), event("USER_LOGOUT", "success")));
        var out = new ByteArrayOutputStream();

        service.exportCsv(FROM, TO, null, out);

        long rowCount = out.toString().lines().count();
        assertThat(rowCount).isEqualTo(3); // header + 2 data rows
    }
}
