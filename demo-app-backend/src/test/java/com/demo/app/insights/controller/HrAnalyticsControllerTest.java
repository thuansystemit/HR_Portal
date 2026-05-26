package com.demo.app.insights.controller;

import com.demo.app.insights.dto.*;
import com.demo.app.insights.service.HrAnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrAnalyticsControllerTest {

    @Mock HrAnalyticsService service;
    @InjectMocks HrAnalyticsController controller;

    // ── funnel ────────────────────────────────────────────────────────────

    @Test
    void funnel_returnsOkWithData() {
        when(service.recruitmentFunnel(null)).thenReturn(List.of(new FunnelStageEntry("APPLIED", 10)));
        var response = controller.funnel(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).stage()).isEqualTo("APPLIED");
    }

    @Test
    void funnel_withJobPostingId_delegatesToService() {
        UUID jobId = UUID.randomUUID();
        when(service.recruitmentFunnel(jobId)).thenReturn(List.of(new FunnelStageEntry("APPLIED", 3)));
        var response = controller.funnel(jobId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(service).recruitmentFunnel(jobId);
    }

    // ── timeToHire ────────────────────────────────────────────────────────

    @Test
    void timeToHire_returnsOkWithData() {
        when(service.timeToHire(null)).thenReturn(List.of(new TimeToHireEntry("Senior Dev", 14.5)));
        var response = controller.timeToHire(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).avgDays()).isEqualTo(14.5);
    }

    @Test
    void timeToHire_withJobPostingId_delegatesToService() {
        UUID jobId = UUID.randomUUID();
        when(service.timeToHire(jobId)).thenReturn(List.of(new TimeToHireEntry("Backend Dev", 9.0)));
        var response = controller.timeToHire(jobId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(service).timeToHire(jobId);
    }

    // ── topSkills ─────────────────────────────────────────────────────────

    @Test
    void topSkills_returnsOkWithData() {
        when(service.topSkills(null)).thenReturn(List.of(new TopSkillEntry("Java", 8)));
        var response = controller.topSkills(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get(0).skillName()).isEqualTo("Java");
    }

    @Test
    void topSkills_withJobPostingId_delegatesToService() {
        UUID jobId = UUID.randomUUID();
        when(service.topSkills(jobId)).thenReturn(List.of(new TopSkillEntry("Kotlin", 2)));
        var response = controller.topSkills(jobId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(service).topSkills(jobId);
    }

    // ── applicationTrend ─────────────────────────────────────────────────

    @Test
    void applicationTrend_returnsOkWithData() {
        when(service.applicationTrend(null)).thenReturn(List.of(new ApplicationTrendEntry("2026-05", 5)));
        var response = controller.applicationTrend(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get(0).month()).isEqualTo("2026-05");
    }

    @Test
    void applicationTrend_withJobPostingId_delegatesToService() {
        UUID jobId = UUID.randomUUID();
        when(service.applicationTrend(jobId)).thenReturn(List.of(new ApplicationTrendEntry("2026-04", 2)));
        var response = controller.applicationTrend(jobId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(service).applicationTrend(jobId);
    }

    // ── conversionRates ───────────────────────────────────────────────────

    @Test
    void conversionRates_returnsOkWithData() {
        when(service.conversionRates(null)).thenReturn(
                List.of(new ConversionRateEntry("APPLIED", "SCREENING", 60.0)));
        var response = controller.conversionRates(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get(0).rate()).isEqualTo(60.0);
    }

    @Test
    void conversionRates_withJobPostingId_delegatesToService() {
        UUID jobId = UUID.randomUUID();
        when(service.conversionRates(jobId)).thenReturn(
                List.of(new ConversionRateEntry("APPLIED", "SCREENING", 50.0)));
        var response = controller.conversionRates(jobId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(service).conversionRates(jobId);
    }
}
