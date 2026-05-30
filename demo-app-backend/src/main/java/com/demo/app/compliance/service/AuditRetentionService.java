package com.demo.app.compliance.service;

import com.demo.app.compliance.entity.AuditEvent;
import com.demo.app.compliance.repository.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPOutputStream;
import java.util.stream.Stream;

/**
 * AU-11: Scheduled archival and purge of audit events older than the retention window.
 *
 * Runs on the 1st of each month at 02:00 UTC. Each run:
 *   1. Streams events older than the retention cutoff and writes them to a NDJSON.gz archive.
 *   2. Opens a maintenance transaction with SET LOCAL app.audit_maintenance = 'true' so the
 *      AU-9 DB trigger allows the bulk DELETE.
 *   3. Bulk-deletes archived rows.
 *
 * The archive path and retention window are configurable via application.yml.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRetentionService {

    private final AuditEventRepository auditEventRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Value("${app.audit.retention.online-days:365}")
    private int retentionDays = 365;

    @Value("${app.audit.archive-path:/app/audit-archives}")
    private String archivePath = "/app/audit-archives";

    @Scheduled(cron = "0 0 2 1 * ?")
    public void runRetentionJob() {
        var cutoff = Instant.now().minusSeconds((long) retentionDays * 86_400);
        log.info("AU-11 retention job starting — cutoff={}, retentionDays={}", cutoff, retentionDays);

        long archived;
        try {
            archived = archiveEvents(cutoff);
        } catch (Exception e) {
            log.error("AU-11 archive write failed — aborting purge to prevent data loss", e);
            return;
        }

        if (archived == 0) {
            log.info("AU-11 retention job complete — no events older than cutoff");
            return;
        }

        int deleted = purgeEvents(cutoff);
        log.info("AU-11 retention job complete — archived={}, purged={}", archived, deleted);
    }

    @Transactional(readOnly = true)
    public long archiveEvents(Instant cutoff) throws IOException {
        var label = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneOffset.UTC)
                .format(cutoff);
        var dir = Paths.get(archivePath);
        Files.createDirectories(dir);
        var file = dir.resolve("audit_archive_" + label + ".ndjson.gz");

        long count = 0;
        try (var out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file)));
             var writer = new OutputStreamWriter(out);
             Stream<AuditEvent> stream = auditEventRepository.streamByDateRange(Instant.EPOCH, cutoff)) {

            for (var it = stream.iterator(); it.hasNext(); ) {
                writer.write(objectMapper.writeValueAsString(it.next()));
                writer.write('\n');
                count++;
            }
        }

        if (count == 0) {
            Files.deleteIfExists(file);
        } else {
            log.info("AU-11 archived {} events to {}", count, file);
        }
        return count;
    }

    @Transactional
    public int purgeEvents(Instant cutoff) {
        // SET LOCAL scopes the GUC to this transaction only; the AU-9 trigger checks it before allowing DELETE.
        entityManager.createNativeQuery("SET LOCAL app.audit_maintenance = 'true'").executeUpdate();
        int deleted = auditEventRepository.deleteByOccurredAtBefore(cutoff);
        log.info("AU-11 purged {} events older than {}", deleted, cutoff);
        return deleted;
    }
}
