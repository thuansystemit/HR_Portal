package com.demo.app.insights.service;

import com.demo.app.insights.dto.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HrAnalyticsService {

    private final EntityManager em;

    // AN-1: candidate counts per pipeline stage (all job postings)
    public List<FunnelStageEntry> recruitmentFunnel() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT stage, COUNT(*) " +
                "FROM job_applications " +
                "GROUP BY stage " +
                "ORDER BY CASE stage " +
                "  WHEN 'APPLIED' THEN 1 WHEN 'SCREENING' THEN 2 WHEN 'INTERVIEW' THEN 3 " +
                "  WHEN 'OFFER' THEN 4 WHEN 'HIRED' THEN 5 WHEN 'REJECTED' THEN 6 ELSE 7 END")
                .getResultList();
        return rows.stream()
                .map(r -> new FunnelStageEntry((String) r[0], ((Number) r[1]).intValue()))
                .toList();
    }

    // AN-2: average days from APPLIED to HIRED per job posting (top 10)
    public List<TimeToHireEntry> timeToHire() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT jp.title, " +
                "  ROUND(AVG(EXTRACT(EPOCH FROM (h.moved_at - ja.applied_at)) / 86400.0)::NUMERIC, 1) " +
                "FROM job_applications ja " +
                "JOIN application_stage_history h ON h.application_id = ja.id AND h.to_stage = 'HIRED' " +
                "JOIN job_postings jp ON jp.id = ja.job_posting_id " +
                "GROUP BY jp.id, jp.title " +
                "ORDER BY 2 DESC " +
                "LIMIT 10")
                .getResultList();
        return rows.stream()
                .map(r -> new TimeToHireEntry((String) r[0], ((Number) r[1]).doubleValue()))
                .toList();
    }

    // AN-3: top 15 skills by unique candidate count
    public List<TopSkillEntry> topSkills() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT skill_name, COUNT(DISTINCT cv_candidate_id) " +
                "FROM cv_technical_skills " +
                "GROUP BY skill_name " +
                "ORDER BY 2 DESC " +
                "LIMIT 15")
                .getResultList();
        return rows.stream()
                .map(r -> new TopSkillEntry((String) r[0], ((Number) r[1]).intValue()))
                .toList();
    }

    // AN-4: monthly application volume — last 12 months
    public List<ApplicationTrendEntry> applicationTrend() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT TO_CHAR(DATE_TRUNC('month', applied_at), 'YYYY-MM'), COUNT(*) " +
                "FROM job_applications " +
                "WHERE applied_at >= NOW() - INTERVAL '12 months' " +
                "GROUP BY DATE_TRUNC('month', applied_at) " +
                "ORDER BY DATE_TRUNC('month', applied_at)")
                .getResultList();
        return rows.stream()
                .map(r -> new ApplicationTrendEntry((String) r[0], ((Number) r[1]).intValue()))
                .toList();
    }

    // AN-5: stage-to-stage conversion rates (APPLIED→SCREENING→INTERVIEW→OFFER→HIRED)
    public List<ConversionRateEntry> conversionRates() {
        List<FunnelStageEntry> funnel = recruitmentFunnel();

        String[] pipeline = {"APPLIED", "SCREENING", "INTERVIEW", "OFFER", "HIRED"};

        var countByStage = funnel.stream()
                .collect(java.util.stream.Collectors.toMap(FunnelStageEntry::stage, FunnelStageEntry::count));

        var result = new ArrayList<ConversionRateEntry>();
        for (int i = 0; i < pipeline.length - 1; i++) {
            String from = pipeline[i];
            String to   = pipeline[i + 1];
            int fromCount = countByStage.getOrDefault(from, 0);
            double rate   = fromCount == 0 ? 0.0
                    : Math.round(countByStage.getOrDefault(to, 0) * 1000.0 / fromCount) / 10.0;
            result.add(new ConversionRateEntry(from, to, rate));
        }
        return result;
    }
}
