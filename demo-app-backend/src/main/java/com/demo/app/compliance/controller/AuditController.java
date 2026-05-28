package com.demo.app.compliance.controller;

import com.demo.app.compliance.service.AuditExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * CA-7: Audit report export endpoint for continuous monitoring.
 * Restricted to users with analyticsView permission.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditExportService auditExportService;

    /**
     * Export audit events as CSV for a given date range.
     *
     * @param from   ISO-8601 instant (inclusive)
     * @param to     ISO-8601 instant (inclusive)
     * @param action optional action code filter (e.g. USER_LOGIN)
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PERM_analyticsView')")
    public void export(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String action,
            HttpServletResponse response) throws IOException {

        Instant fromInstant;
        Instant toInstant;
        try {
            fromInstant = Instant.parse(from);
            toInstant   = Instant.parse(to);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "from and to must be ISO-8601 instants (e.g. 2026-01-01T00:00:00Z)");
            return;
        }

        if (!toInstant.isAfter(fromInstant)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "'to' must be after 'from'");
            return;
        }

        long days = ChronoUnit.DAYS.between(fromInstant, toInstant);
        if (days > AuditExportService.MAX_RANGE_DAYS) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Date range must not exceed " + AuditExportService.MAX_RANGE_DAYS + " days");
            return;
        }

        String safeFrom = from.replace(":", "-");
        String safeTo   = to.replace(":", "-");
        String filename = "audit_export_" + safeFrom + "_" + safeTo + ".csv";

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-Control", "no-store");

        log.info("Audit export requested: from={} to={} action={}", from, to, action);
        auditExportService.exportCsv(fromInstant, toInstant, action, response.getOutputStream());
    }
}
