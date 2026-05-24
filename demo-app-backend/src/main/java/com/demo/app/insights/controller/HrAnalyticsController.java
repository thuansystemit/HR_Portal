package com.demo.app.insights.controller;

import com.demo.app.insights.dto.*;
import com.demo.app.insights.service.HrAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/hr")
@RequiredArgsConstructor
public class HrAnalyticsController {

    private final HrAnalyticsService hrAnalyticsService;

    @GetMapping("/funnel")
    public ResponseEntity<List<FunnelStageEntry>> funnel() {
        return ResponseEntity.ok(hrAnalyticsService.recruitmentFunnel());
    }

    @GetMapping("/time-to-hire")
    public ResponseEntity<List<TimeToHireEntry>> timeToHire() {
        return ResponseEntity.ok(hrAnalyticsService.timeToHire());
    }

    @GetMapping("/top-skills")
    public ResponseEntity<List<TopSkillEntry>> topSkills() {
        return ResponseEntity.ok(hrAnalyticsService.topSkills());
    }

    @GetMapping("/application-trend")
    public ResponseEntity<List<ApplicationTrendEntry>> applicationTrend() {
        return ResponseEntity.ok(hrAnalyticsService.applicationTrend());
    }

    @GetMapping("/conversion-rates")
    public ResponseEntity<List<ConversionRateEntry>> conversionRates() {
        return ResponseEntity.ok(hrAnalyticsService.conversionRates());
    }
}
