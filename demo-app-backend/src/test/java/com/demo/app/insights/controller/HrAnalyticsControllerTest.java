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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrAnalyticsControllerTest {

    @Mock HrAnalyticsService service;
    @InjectMocks HrAnalyticsController controller;

    @Test
    void funnel_returnsOkWithData() {
        when(service.recruitmentFunnel()).thenReturn(List.of(new FunnelStageEntry("APPLIED", 10)));
        var response = controller.funnel();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).stage()).isEqualTo("APPLIED");
    }

    @Test
    void timeToHire_returnsOkWithData() {
        when(service.timeToHire()).thenReturn(List.of(new TimeToHireEntry("Senior Dev", 14.5)));
        var response = controller.timeToHire();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).avgDays()).isEqualTo(14.5);
    }

    @Test
    void topSkills_returnsOkWithData() {
        when(service.topSkills()).thenReturn(List.of(new TopSkillEntry("Java", 8)));
        var response = controller.topSkills();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get(0).skillName()).isEqualTo("Java");
    }

    @Test
    void applicationTrend_returnsOkWithData() {
        when(service.applicationTrend()).thenReturn(List.of(new ApplicationTrendEntry("2026-05", 5)));
        var response = controller.applicationTrend();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get(0).month()).isEqualTo("2026-05");
    }

    @Test
    void conversionRates_returnsOkWithData() {
        when(service.conversionRates()).thenReturn(
                List.of(new ConversionRateEntry("APPLIED", "SCREENING", 60.0)));
        var response = controller.conversionRates();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get(0).rate()).isEqualTo(60.0);
    }
}
