package com.demo.app.content.dto;

import org.springframework.core.io.Resource;

public record DownloadResult(Resource resource, String mimeType, String filename) {}
