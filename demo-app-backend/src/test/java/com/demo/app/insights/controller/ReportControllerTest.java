package com.demo.app.insights.controller;

import com.demo.app.insights.dto.CategoryCountEntry;
import com.demo.app.insights.dto.RoleDistributionEntry;
import com.demo.app.insights.dto.StorageEntry;
import com.demo.app.insights.dto.UploadTrendEntry;
import com.demo.app.insights.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController reportController;

    @Test
    void uploadTrend_returnsOk() {
        var entry = new UploadTrendEntry("Docs", "2024-01", 5);
        when(reportService.uploadTrend()).thenReturn(List.of(entry));

        var result = reportController.uploadTrend();

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).categoryName()).isEqualTo("Docs");
    }

    @Test
    void categoryCount_returnsOk() {
        var entry = new CategoryCountEntry("Docs", 10);
        when(reportService.categoryCount()).thenReturn(List.of(entry));

        var result = reportController.categoryCount();

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).count()).isEqualTo(10);
    }

    @Test
    void storage_returnsOk() {
        var entry = new StorageEntry("Docs", 1024L);
        when(reportService.storageByCategory()).thenReturn(List.of(entry));

        var result = reportController.storage();

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).totalBytes()).isEqualTo(1024L);
    }

    @Test
    void roleDistribution_returnsOk() {
        var entry = new RoleDistributionEntry("Admin", 3);
        when(reportService.roleDistribution()).thenReturn(List.of(entry));

        var result = reportController.roleDistribution();

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).userCount()).isEqualTo(3);
    }
}
