package com.demo.app.insights.controller;

import com.demo.app.insights.dto.*;
import com.demo.app.insights.service.HrAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/hr")
@RequiredArgsConstructor
public class HrAnalyticsController {

    private final HrAnalyticsService hrAnalyticsService;

    @GetMapping("/funnel")
    public ResponseEntity<List<FunnelStageEntry>> funnel(
            @RequestParam(required = false) UUID jobPostingId) {
        return ResponseEntity.ok(hrAnalyticsService.recruitmentFunnel(jobPostingId));
    }

    @GetMapping("/time-to-hire")
    public ResponseEntity<List<TimeToHireEntry>> timeToHire(
            @RequestParam(required = false) UUID jobPostingId) {
        return ResponseEntity.ok(hrAnalyticsService.timeToHire(jobPostingId));
    }

    @GetMapping("/top-skills")
    public ResponseEntity<List<TopSkillEntry>> topSkills(
            @RequestParam(required = false) UUID jobPostingId) {
        return ResponseEntity.ok(hrAnalyticsService.topSkills(jobPostingId));
    }

    @GetMapping("/application-trend")
    public ResponseEntity<List<ApplicationTrendEntry>> applicationTrend(
            @RequestParam(required = false) UUID jobPostingId) {
        return ResponseEntity.ok(hrAnalyticsService.applicationTrend(jobPostingId));
    }

    @GetMapping("/conversion-rates")
    public ResponseEntity<List<ConversionRateEntry>> conversionRates(
            @RequestParam(required = false) UUID jobPostingId) {
        return ResponseEntity.ok(hrAnalyticsService.conversionRates(jobPostingId));
    }
}
