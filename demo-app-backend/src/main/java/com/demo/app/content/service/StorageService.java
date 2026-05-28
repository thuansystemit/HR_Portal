package com.demo.app.content.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final Path uploadRoot;

    public void store(String relativePath, MultipartFile file) {
        try {
            Path target = uploadRoot.resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file: " + relativePath, e);
        }
    }

    public void store(String relativePath, byte[] bytes) {
        try {
            Path target = uploadRoot.resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file: " + relativePath, e);
        }
    }

    public Resource load(String relativePath) {
        return new FileSystemResource(uploadRoot.resolve(relativePath));
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(uploadRoot.resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file: " + relativePath, e);
        }
    }
}
