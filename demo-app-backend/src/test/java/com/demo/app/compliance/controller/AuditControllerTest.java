package com.demo.app.compliance.controller;

import com.demo.app.compliance.dto.AuditEventResponse;
import com.demo.app.compliance.service.AuditExportService;
import com.demo.app.compliance.service.AuditQueryService;
import com.demo.app.compliance.service.AuditService;
import com.demo.app.iam.dto.PagedResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock AuditExportService auditExportService;
    @Mock AuditQueryService auditQueryService;
    @Mock AuditService auditService;
    @InjectMocks AuditController controller;

    private MockHttpServletResponse response;
    private final String USER_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
    }

    @Test
    void export_setsCsvContentType() throws IOException {
        controller.export(USER_ID, "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
    }

    @Test
    void export_setsContentDispositionHeader() throws IOException {
        controller.export(USER_ID, "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        assertThat(response.getHeader("Content-Disposition"))
                .startsWith("attachment; filename=")
                .contains("audit_export_");
    }

    @Test
    void export_setsCacheControlNoStore() throws IOException {
        controller.export(USER_ID, "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void export_delegatesToService_withCorrectInstants() throws IOException {
        controller.export(USER_ID, "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", "USER_LOGIN", response);

        verify(auditExportService).exportCsv(
                eq(Instant.parse("2026-01-01T00:00:00Z")),
                eq(Instant.parse("2026-01-31T23:59:59Z")),
                eq("USER_LOGIN"),
                any());
    }

    @Test
    void export_returns400_whenToBeforeFrom() throws IOException {
        controller.export(USER_ID, "2026-01-31T00:00:00Z", "2026-01-01T00:00:00Z", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
        verifyNoInteractions(auditService);
    }

    @Test
    void export_returns400_whenToEqualsFrom() throws IOException {
        controller.export(USER_ID, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
        verifyNoInteractions(auditService);
    }

    @Test
    void export_returns400_whenRangeExceedsMax() throws IOException {
        controller.export(USER_ID, "2025-01-01T00:00:00Z", "2027-01-01T00:00:00Z", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
        verifyNoInteractions(auditService);
    }

    @Test
    void export_returns400_whenFromIsInvalidInstant() throws IOException {
        controller.export(USER_ID, "not-a-date", "2026-01-31T00:00:00Z", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
        verifyNoInteractions(auditService);
    }

    @Test
    void export_returns400_whenToIsInvalidInstant() throws IOException {
        controller.export(USER_ID, "2026-01-01T00:00:00Z", "bad", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
        verifyNoInteractions(auditService);
    }

    @Test
    void export_passesNullAction_whenNotProvided() throws IOException {
        controller.export(USER_ID, "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        verify(auditExportService).exportCsv(any(), any(), isNull(), any());
    }

    @Test
    void export_emitsAuditEvent_withActorAndQueryParams() throws IOException {
        // AC-6(9): downloading the audit log is a privileged function and must be self-audited
        controller.export(USER_ID, "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", "USER_LOGIN", response);

        verify(auditService).log(
                eq(UUID.fromString(USER_ID)),
                eq("AUDIT_EXPORT_DOWNLOADED"),
                eq("AuditExport"),
                isNull(),
                isNull(),
                argThat(m -> "2026-01-01T00:00:00Z".equals(m.get("from"))
                        && "2026-01-31T23:59:59Z".equals(m.get("to"))
                        && "USER_LOGIN".equals(m.get("action"))),
                eq("success"));
    }

    @Test
    void export_emitsAuditEvent_withActionAll_whenFilterIsNull() throws IOException {
        controller.export(USER_ID, "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        verify(auditService).log(any(), eq("AUDIT_EXPORT_DOWNLOADED"), any(), any(), any(),
                argThat(m -> "ALL".equals(m.get("action"))), eq("success"));
    }

    @Test
    void export_emitsAuditEvent_withNullActor_whenUserIdIsNull() throws IOException {
        // covers the userId == null ternary branch
        controller.export(null, "2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        verify(auditService).log(isNull(), eq("AUDIT_EXPORT_DOWNLOADED"), any(), any(), any(), any(), eq("success"));
    }

    @Test
    void export_doesNotEmitAuditEvent_onValidationError() throws IOException {
        controller.export(USER_ID, "bad-date", "2026-01-31T23:59:59Z", null, response);

        verifyNoInteractions(auditService);
    }

    // AU-6: /events endpoint tests

    private PagedResponse<AuditEventResponse> emptyPage() {
        return new PagedResponse<>(List.of(), 0, 20, 0, 0);
    }

    @Test
    void events_returns200_withPagedResponse() {
        when(auditQueryService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(emptyPage());

        var result = controller.events(null, null, null, null, null, 0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isInstanceOf(PagedResponse.class);
    }

    @Test
    void events_passesAllFiltersToService() {
        var actorId = UUID.randomUUID();
        when(auditQueryService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(emptyPage());

        controller.events("2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z",
                "USER_LOGIN", actorId, "User", 1, 50);

        verify(auditQueryService).search(
                eq(Instant.parse("2026-01-01T00:00:00Z")),
                eq(Instant.parse("2026-01-31T23:59:59Z")),
                eq("USER_LOGIN"), eq(actorId), eq("User"), eq(1), eq(50));
    }

    @Test
    void events_passesNullInstants_whenFromAndToOmitted() {
        when(auditQueryService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(emptyPage());

        controller.events(null, null, null, null, null, 0, 20);

        verify(auditQueryService).search(isNull(), isNull(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void events_returns400_whenFromIsInvalidInstant() {
        var result = controller.events("not-a-date", "2026-01-31T23:59:59Z",
                null, null, null, 0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(auditQueryService);
    }

    @Test
    void events_returns400_whenToIsInvalidInstant() {
        var result = controller.events("2026-01-01T00:00:00Z", "bad",
                null, null, null, 0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(auditQueryService);
    }

    @Test
    void events_returns400_whenToBeforeFrom() {
        var result = controller.events("2026-01-31T00:00:00Z", "2026-01-01T00:00:00Z",
                null, null, null, 0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(auditQueryService);
    }

    @Test
    void events_returns400_whenToEqualsFrom() {
        var result = controller.events("2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z",
                null, null, null, 0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(auditQueryService);
    }
}
