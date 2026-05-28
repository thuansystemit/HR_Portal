package com.demo.app.content.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageServiceTest {

    @TempDir
    Path tempDir;

    StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(tempDir);
    }

    @Test
    void store_writesFileToDisk() throws IOException {
        var file = mock(MultipartFile.class);
        byte[] content = "test file content".getBytes();
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));

        storageService.store("docs/test.pdf", file);

        Path target = tempDir.resolve("docs/test.pdf");
        assertThat(target).exists();
        assertThat(Files.readAllBytes(target)).isEqualTo(content);
    }

    @Test
    void store_createsParentDirectories() throws IOException {
        var file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        storageService.store("a/b/c/file.txt", file);

        assertThat(tempDir.resolve("a/b/c/file.txt")).exists();
        assertThat(tempDir.resolve("a/b/c")).isDirectory();
    }

    @Test
    void load_returnsResource() throws IOException {
        Path target = tempDir.resolve("existing.txt");
        Files.writeString(target, "hello");

        var resource = storageService.load("existing.txt");

        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
    }

    @Test
    void delete_removesFile() throws IOException {
        Path target = tempDir.resolve("to-delete.txt");
        Files.writeString(target, "bye");

        storageService.delete("to-delete.txt");

        assertThat(target).doesNotExist();
    }

    @Test
    void delete_succeedsWhenFileDoesNotExist() {
        assertThatCode(() -> storageService.delete("nonexistent.txt"))
                .doesNotThrowAnyException();
    }

    @Test
    void storeBytes_writesFileToDisk() throws IOException {
        byte[] content = "byte content".getBytes();

        storageService.store("bytes/test.bin", content);

        Path target = tempDir.resolve("bytes/test.bin");
        assertThat(target).exists();
        assertThat(Files.readAllBytes(target)).isEqualTo(content);
    }

    @Test
    void storeBytes_createsParentDirectories() throws IOException {
        byte[] content = "data".getBytes();

        storageService.store("a/b/c/deep.bin", content);

        assertThat(tempDir.resolve("a/b/c/deep.bin")).exists();
        assertThat(tempDir.resolve("a/b/c")).isDirectory();
    }

    @Test
    void storeBytes_throwsUncheckedIOException_whenPathIsInvalid() throws IOException {
        // Write a file where we then try to use as a directory
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "occupied");

        // Try to store inside a file-as-directory — createDirectories will fail
        assertThatCode(() -> storageService.store("blocker/child.bin", new byte[]{1}))
                .isInstanceOf(java.io.UncheckedIOException.class);
    }
}
