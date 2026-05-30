package com.demo.app.compliance.service;

import com.demo.app.platform.metrics.SecurityEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupVerificationServiceTest {

    @Mock AuditService auditService;
    @Mock SecurityEventRecorder securityEventRecorder;

    @InjectMocks
    BackupVerificationService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxAgeHours", 25);
    }

    @Test
    void health_returnsUnknown_whenPathBlank() {
        ReflectionTestUtils.setField(service, "backupPath", "");

        var h = service.health();

        assertThat(h.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(h.getDetails()).containsKey("reason");
    }

    @Test
    void health_returnsDown_whenFileDoesNotExist() {
        ReflectionTestUtils.setField(service, "backupPath", tempDir.resolve("nonexistent.manifest").toString());

        var h = service.health();

        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails().get("reason")).isEqualTo("backup manifest not found");
    }

    @Test
    void health_returnsDown_whenBackupIsStale() throws IOException {
        File manifest = tempDir.resolve("backup.manifest").toFile();
        manifest.createNewFile();
        // set last-modified to 30 hours ago
        manifest.setLastModified(System.currentTimeMillis() - 30L * 3600 * 1000);
        ReflectionTestUtils.setField(service, "backupPath", manifest.getAbsolutePath());

        var h = service.health();

        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails().get("reason")).isEqualTo("backup is stale");
    }

    @Test
    void health_returnsUp_whenBackupIsRecent() throws IOException {
        File manifest = tempDir.resolve("backup.manifest").toFile();
        manifest.createNewFile();
        // just created — age is 0 h
        ReflectionTestUtils.setField(service, "backupPath", manifest.getAbsolutePath());

        var h = service.health();

        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsKey("lastBackup");
        assertThat(h.getDetails()).containsKey("ageHours");
    }

    @Test
    void health_returnsUp_atBoundary_notExceeding() throws IOException {
        File manifest = tempDir.resolve("backup.manifest").toFile();
        manifest.createNewFile();
        // set to exactly maxAgeHours - 1 hours ago
        manifest.setLastModified(System.currentTimeMillis() - 24L * 3600 * 1000);
        ReflectionTestUtils.setField(service, "backupPath", manifest.getAbsolutePath());

        var h = service.health();

        assertThat(h.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void verifyBackup_whenDisabled_skipsAll() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.verifyBackup();

        verifyNoInteractions(auditService, securityEventRecorder);
    }

    @Test
    void verifyBackup_emitsPassedAudit_whenHealthIsUp() throws IOException {
        File manifest = tempDir.resolve("backup.manifest").toFile();
        manifest.createNewFile();
        ReflectionTestUtils.setField(service, "backupPath", manifest.getAbsolutePath());

        service.verifyBackup();

        verify(auditService).log(
                eq(BackupVerificationService.SYSTEM_ACTOR),
                eq("BACKUP_VERIFICATION_PASSED"),
                eq("System"),
                isNull(),
                isNull(),
                any(),
                eq("success"));
        verify(securityEventRecorder, never()).recordBackupVerificationFailed();
    }

    @Test
    void verifyBackup_emitsFailedAudit_andIncrementsCounter_whenHealthIsDown() {
        ReflectionTestUtils.setField(service, "backupPath", tempDir.resolve("missing.manifest").toString());

        service.verifyBackup();

        verify(auditService).log(
                eq(BackupVerificationService.SYSTEM_ACTOR),
                eq("BACKUP_VERIFICATION_FAILED"),
                eq("System"),
                isNull(),
                isNull(),
                any(),
                eq("failure"));
        verify(securityEventRecorder).recordBackupVerificationFailed();
    }

    @Test
    void verifyBackup_staleFile_emitsFailedAuditAndCounter() throws IOException {
        File manifest = tempDir.resolve("backup.manifest").toFile();
        manifest.createNewFile();
        manifest.setLastModified(System.currentTimeMillis() - 30L * 3600 * 1000);
        ReflectionTestUtils.setField(service, "backupPath", manifest.getAbsolutePath());

        service.verifyBackup();

        verify(auditService).log(any(), eq("BACKUP_VERIFICATION_FAILED"), any(), any(), any(), any(), eq("failure"));
        verify(securityEventRecorder).recordBackupVerificationFailed();
    }
}
