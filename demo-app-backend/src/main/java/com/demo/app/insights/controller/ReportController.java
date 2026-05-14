package com.demo.app.insights.controller;

import com.demo.app.insights.dto.*;
import com.demo.app.insights.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/upload-trend")
    public ResponseEntity<List<UploadTrendEntry>> uploadTrend() {
        return ResponseEntity.ok(reportService.uploadTrend());
    }

    @GetMapping("/category-count")
    public ResponseEntity<List<CategoryCountEntry>> categoryCount() {
        return ResponseEntity.ok(reportService.categoryCount());
    }

    @GetMapping("/storage")
    public ResponseEntity<List<StorageEntry>> storage() {
        return ResponseEntity.ok(reportService.storageByCategory());
    }

    @GetMapping("/role-distribution")
    public ResponseEntity<List<RoleDistributionEntry>> roleDistribution() {
        return ResponseEntity.ok(reportService.roleDistribution());
    }
}
