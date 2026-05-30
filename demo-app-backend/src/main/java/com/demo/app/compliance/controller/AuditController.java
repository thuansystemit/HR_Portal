package com.demo.app.compliance.controller;

import com.demo.app.compliance.dto.AuditEventResponse;
import com.demo.app.compliance.service.AuditExportService;
import com.demo.app.compliance.service.AuditQueryService;
import com.demo.app.compliance.service.AuditService;
import com.demo.app.iam.dto.PagedResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
    private final AuditQueryService auditQueryService;
    private final AuditService auditService;

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PERM_analyticsView')")
    public void export(
            @AuthenticationPrincipal String userId,
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

        auditExportService.exportCsv(fromInstant, toInstant, action, response.getOutputStream());

        // AC-6(9): downloading the full audit log is itself a privileged function — record who ran it
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from);
        details.put("to", to);
        details.put("action", action != null ? action : "ALL");
        auditService.log(userId != null ? UUID.fromString(userId) : null,
                "AUDIT_EXPORT_DOWNLOADED", "AuditExport", null, null, details, "success");
    }

    // AU-6: paginated audit event search for continuous monitoring review
    @GetMapping("/events")
    @PreAuthorize("hasAuthority('PERM_analyticsView')")
    public ResponseEntity<?> events(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Instant fromInstant = null;
        Instant toInstant = null;
        try {
            if (from != null) fromInstant = Instant.parse(from);
            if (to != null) toInstant = Instant.parse(to);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("'from' and 'to' must be ISO-8601 instants (e.g. 2026-01-01T00:00:00Z)");
        }

        if (fromInstant != null && toInstant != null && !toInstant.isAfter(fromInstant)) {
            return ResponseEntity.badRequest().body("'to' must be after 'from'");
        }

        PagedResponse<AuditEventResponse> result =
                auditQueryService.search(fromInstant, toInstant, action, actorId, entityType, page, size);
        return ResponseEntity.ok(result);
    }
}
