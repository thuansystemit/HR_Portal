package com.demo.app.compliance.service;

import com.demo.app.platform.metrics.SecurityEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileIntegrityServiceTest {

    @Mock AuditService auditService;
    @Mock SecurityEventRecorder securityEventRecorder;

    @InjectMocks
    FileIntegrityService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "failOnViolation", false);
    }

    @Test
    void run_whenDisabled_skipsAll() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.run(null);

        verifyNoInteractions(auditService, securityEventRecorder);
    }

    @Test
    void run_whenManifestPathBlank_skipsChecks() throws Exception {
        ReflectionTestUtils.setField(service, "manifestPath", "");

        service.run(null);

        verifyNoInteractions(auditService, securityEventRecorder);
    }

    @Test
    void run_whenManifestFileMissing_skipsChecks() throws Exception {
        ReflectionTestUtils.setField(service, "manifestPath",
                tempDir.resolve("nonexistent.manifest").toString());

        service.run(null);

        verifyNoInteractions(auditService, securityEventRecorder);
    }

    @Test
    void run_allFilesClean_emitsPassedAuditWithCount() throws Exception {
        var file = tempDir.resolve("app.jar");
        Files.writeString(file, "binary content");
        var hash = service.sha256(file);

        var manifest = tempDir.resolve("integrity.manifest");
        Files.writeString(manifest, String.format("%s  %s%n", hash, file));
        ReflectionTestUtils.setField(service, "manifestPath", manifest.toString());

        service.run(null);

        verify(auditService).log(
                eq(FileIntegrityService.SYSTEM_ACTOR),
                eq("FILE_INTEGRITY_CHECK_PASSED"),
                eq("System"),
                isNull(), isNull(),
                argThat(m -> "1".equals(m.get("filesChecked"))),
                eq("success"));
        verify(securityEventRecorder, never()).recordIntegrityViolation();
    }

    @Test
    void run_fileMissingFromDisk_emitsViolationAuditAndCounter() throws Exception {
        var missingPath = tempDir.resolve("missing.jar").toString();
        var manifest = tempDir.resolve("integrity.manifest");
        Files.writeString(manifest, String.format("abc123def456  %s%n", missingPath));
        ReflectionTestUtils.setField(service, "manifestPath", manifest.toString());

        service.run(null);

        verify(auditService).log(
                eq(FileIntegrityService.SYSTEM_ACTOR),
                eq("FILE_INTEGRITY_VIOLATION"),
                eq("System"),
                isNull(), isNull(),
                argThat(m -> missingPath.equals(m.get("file"))),
                eq("failure"));
        verify(securityEventRecorder).recordIntegrityViolation();
    }

    @Test
    void run_hashMismatch_emitsViolationAuditAndCounter() throws Exception {
        var file = tempDir.resolve("app.jar");
        Files.writeString(file, "real content");
        var manifest = tempDir.resolve("integrity.manifest");
        Files.writeString(manifest, String.format("0000000000000000  %s%n", file));
        ReflectionTestUtils.setField(service, "manifestPath", manifest.toString());

        service.run(null);

        verify(auditService).log(any(), eq("FILE_INTEGRITY_VIOLATION"), any(), any(), any(), any(), eq("failure"));
        verify(securityEventRecorder).recordIntegrityViolation();
    }

    @Test
    void run_violation_withFailOnViolation_throwsIllegalState() throws Exception {
        ReflectionTestUtils.setField(service, "failOnViolation", true);
        var missingPath = tempDir.resolve("gone.jar").toString();
        var manifest = tempDir.resolve("integrity.manifest");
        Files.writeString(manifest, String.format("deadbeef  %s%n", missingPath));
        ReflectionTestUtils.setField(service, "manifestPath", manifest.toString());

        assertThatThrownBy(() -> service.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SI-7");
    }

    @Test
    void run_violation_withoutFailOnViolation_continuesNormally() throws Exception {
        ReflectionTestUtils.setField(service, "failOnViolation", false);
        var missingPath = tempDir.resolve("gone.jar").toString();
        var manifest = tempDir.resolve("integrity.manifest");
        Files.writeString(manifest, String.format("deadbeef  %s%n", missingPath));
        ReflectionTestUtils.setField(service, "manifestPath", manifest.toString());

        assertThatCode(() -> service.run(null)).doesNotThrowAnyException();
        verify(securityEventRecorder).recordIntegrityViolation();
    }

    @Test
    void run_multipleViolations_emitsOneAuditAndCounterPerViolation() throws Exception {
        var p1 = tempDir.resolve("a.jar").toString();
        var p2 = tempDir.resolve("b.jar").toString();
        var manifest = tempDir.resolve("integrity.manifest");
        Files.writeString(manifest, String.format("aaa  %s%naaa  %s%n", p1, p2));
        ReflectionTestUtils.setField(service, "manifestPath", manifest.toString());

        service.run(null);

        verify(auditService, times(2)).log(any(), eq("FILE_INTEGRITY_VIOLATION"), any(), any(), any(), any(), eq("failure"));
        verify(securityEventRecorder, times(2)).recordIntegrityViolation();
    }

    @Test
    void sha256_returnsEmpty_whenFileUnreadable() {
        var result = service.sha256(Path.of("/nonexistent/path/file.bin"));
        assertThat(result).isEmpty();
    }

    @Test
    void parseManifest_skipsBlankAndCommentLines() throws Exception {
        var manifest = tempDir.resolve("test.manifest");
        Files.writeString(manifest,
                "# this is a comment\n" +
                "\n" +
                "abc123  /app/file.jar\n" +
                "def456  /app/config.yml\n" +
                "malformed-no-whitespace\n");

        var result = service.parseManifest(manifest);

        assertThat(result).hasSize(2)
                .containsEntry("/app/file.jar", "abc123")
                .containsEntry("/app/config.yml", "def456");
    }
}
