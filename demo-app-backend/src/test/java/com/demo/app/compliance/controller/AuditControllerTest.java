package com.demo.app.compliance.controller;

import com.demo.app.compliance.service.AuditExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock AuditExportService auditExportService;
    @InjectMocks AuditController controller;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
    }

    @Test
    void export_setsCsvContentType() throws IOException {
        controller.export("2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
    }

    @Test
    void export_setsContentDispositionHeader() throws IOException {
        controller.export("2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        assertThat(response.getHeader("Content-Disposition"))
                .startsWith("attachment; filename=")
                .contains("audit_export_");
    }

    @Test
    void export_setsCacheControlNoStore() throws IOException {
        controller.export("2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void export_delegatesToService_withCorrectInstants() throws IOException {
        controller.export("2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", "USER_LOGIN", response);

        verify(auditExportService).exportCsv(
                eq(Instant.parse("2026-01-01T00:00:00Z")),
                eq(Instant.parse("2026-01-31T23:59:59Z")),
                eq("USER_LOGIN"),
                any());
    }

    @Test
    void export_returns400_whenToBeforeFrom() throws IOException {
        controller.export("2026-01-31T00:00:00Z", "2026-01-01T00:00:00Z", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
    }

    @Test
    void export_returns400_whenToEqualsFrom() throws IOException {
        controller.export("2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
    }

    @Test
    void export_returns400_whenRangeExceedsMax() throws IOException {
        controller.export("2025-01-01T00:00:00Z", "2027-01-01T00:00:00Z", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
    }

    @Test
    void export_returns400_whenFromIsInvalidInstant() throws IOException {
        controller.export("not-a-date", "2026-01-31T00:00:00Z", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
    }

    @Test
    void export_returns400_whenToIsInvalidInstant() throws IOException {
        controller.export("2026-01-01T00:00:00Z", "bad", null, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(auditExportService);
    }

    @Test
    void export_passesNullAction_whenNotProvided() throws IOException {
        controller.export("2026-01-01T00:00:00Z", "2026-01-31T23:59:59Z", null, response);

        verify(auditExportService).exportCsv(any(), any(), isNull(), any());
    }
}
