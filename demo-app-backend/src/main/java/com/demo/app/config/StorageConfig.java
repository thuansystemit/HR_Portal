package com.demo.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class StorageConfig {

    @Value("${app.storage.upload-dir:/app/uploads}")
    private String uploadDir;

    @Bean
    public Path uploadRoot() throws IOException {
        var root = Path.of(uploadDir);
        Files.createDirectories(root);
        return root;
    }
}
