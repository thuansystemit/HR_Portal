package com.demo.app.compliance.service;

import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * CA-7: Streams audit events as CSV for continuous monitoring export.
 * Uses cursor-based JPA streaming (fetchSize=500) to avoid loading the full
 * result set into memory. Must be called within a transaction so the DB
 * cursor stays open for the lifetime of the stream.
 */
@Service
@RequiredArgsConstructor
public class AuditExportService {

    static final String CSV_HEADER =
            "id,actorId,action,entityType,entityId,outcome,ipAddress,correlationId,sessionId,occurredAt\n";

    public static final int MAX_RANGE_DAYS = 366;

    private final AuditEventRepository repository;

    @Transactional(readOnly = true)
    public void exportCsv(Instant from, Instant to, String action, OutputStream out) throws IOException {
        Stream<AuditEvent> stream = (action != null && !action.isBlank())
                ? repository.streamByDateRangeAndAction(from, to, action)
                : repository.streamByDateRange(from, to);

        try (stream;
             var writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.write(CSV_HEADER);
            stream.forEach(event -> {
                try {
                    writer.write(toCsvRow(event));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            writer.flush();
        }
    }

    private String toCsvRow(AuditEvent e) {
        return String.join(",",
                csv(e.getId()),
                csv(e.getActorId()),
                csv(e.getAction()),
                csv(e.getEntityType()),
                csv(e.getEntityId()),
                csv(e.getOutcome()),
                csv(e.getIpAddress()),
                csv(e.getCorrelationId()),
                csv(e.getSessionId()),
                csv(e.getOccurredAt())
        ) + "\n";
    }

    String csv(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
