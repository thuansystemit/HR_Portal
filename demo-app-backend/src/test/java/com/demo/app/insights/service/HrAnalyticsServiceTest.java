package com.demo.app.insights.service;

import com.demo.app.insights.dto.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HrAnalyticsServiceTest {

    @Mock EntityManager em;
    @Mock Query query;

    @InjectMocks
    HrAnalyticsService service;

    private static List<Object[]> rows(Object[]... rowArrays) {
        var list = new ArrayList<Object[]>();
        for (var r : rowArrays) list.add(r);
        return list;
    }

    // ── recruitmentFunnel ──────────────────────────────────────────────────

    @Test
    void funnel_mapsRowsCorrectly() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows(
                new Object[]{"APPLIED", 10L},
                new Object[]{"SCREENING", 5L},
                new Object[]{"HIRED", 2L}
        ));

        List<FunnelStageEntry> result = service.recruitmentFunnel();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).stage()).isEqualTo("APPLIED");
        assertThat(result.get(0).count()).isEqualTo(10);
        assertThat(result.get(1).stage()).isEqualTo("SCREENING");
        assertThat(result.get(2).count()).isEqualTo(2);
    }

    @Test
    void funnel_emptyTable_returnsEmptyList() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(service.recruitmentFunnel()).isEmpty();
    }

    // ── timeToHire ─────────────────────────────────────────────────────────

    @Test
    void timeToHire_mapsRowsCorrectly() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows(
                new Object[]{"Senior Java Developer", 12.5},
                new Object[]{"Frontend Engineer", 8.0}
        ));

        List<TimeToHireEntry> result = service.timeToHire();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).jobTitle()).isEqualTo("Senior Java Developer");
        assertThat(result.get(0).avgDays()).isEqualTo(12.5);
        assertThat(result.get(1).avgDays()).isEqualTo(8.0);
    }

    @Test
    void timeToHire_noHiredCandidates_returnsEmptyList() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(service.timeToHire()).isEmpty();
    }

    // ── topSkills ──────────────────────────────────────────────────────────

    @Test
    void topSkills_mapsRowsCorrectly() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows(
                new Object[]{"Java", 8L},
                new Object[]{"Spring Boot", 6L},
                new Object[]{"React", 4L}
        ));

        List<TopSkillEntry> result = service.topSkills();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).skillName()).isEqualTo("Java");
        assertThat(result.get(0).candidateCount()).isEqualTo(8);
    }

    @Test
    void topSkills_noCandidates_returnsEmptyList() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(service.topSkills()).isEmpty();
    }

    // ── applicationTrend ──────────────────────────────────────────────────

    @Test
    void applicationTrend_mapsRowsCorrectly() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows(
                new Object[]{"2026-01", 3L},
                new Object[]{"2026-02", 7L},
                new Object[]{"2026-03", 5L}
        ));

        List<ApplicationTrendEntry> result = service.applicationTrend();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).month()).isEqualTo("2026-01");
        assertThat(result.get(1).count()).isEqualTo(7);
    }

    @Test
    void applicationTrend_noData_returnsEmptyList() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(service.applicationTrend()).isEmpty();
    }

    // ── conversionRates ───────────────────────────────────────────────────

    @Test
    void conversionRates_computesRatesFromFunnel() {
        // recruitmentFunnel() calls em.createNativeQuery internally
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows(
                new Object[]{"APPLIED",   100L},
                new Object[]{"SCREENING",  60L},
                new Object[]{"INTERVIEW",  30L},
                new Object[]{"OFFER",      10L},
                new Object[]{"HIRED",       8L},
                new Object[]{"REJECTED",   20L}
        ));

        List<ConversionRateEntry> result = service.conversionRates();

        assertThat(result).hasSize(4);
        // APPLIED(100) → SCREENING(60) = 60%
        assertThat(result.get(0).fromStage()).isEqualTo("APPLIED");
        assertThat(result.get(0).toStage()).isEqualTo("SCREENING");
        assertThat(result.get(0).rate()).isEqualTo(60.0);
        // SCREENING(60) → INTERVIEW(30) = 50%
        assertThat(result.get(1).rate()).isEqualTo(50.0);
        // INTERVIEW(30) → OFFER(10) ≈ 33.3%
        assertThat(result.get(2).rate()).isEqualTo(33.3);
        // OFFER(10) → HIRED(8) = 80%
        assertThat(result.get(3).rate()).isEqualTo(80.0);
    }

    @Test
    void conversionRates_emptyFunnel_allRatesZero() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        List<ConversionRateEntry> result = service.conversionRates();

        assertThat(result).hasSize(4);
        result.forEach(e -> assertThat(e.rate()).isEqualTo(0.0));
    }

    @Test
    void conversionRates_missingIntermediateStage_usesZeroCount() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        // Only APPLIED present — all subsequent stages missing
        when(query.getResultList()).thenReturn(rows(
                new Object[]{"APPLIED", 50L}
        ));

        List<ConversionRateEntry> result = service.conversionRates();

        assertThat(result.get(0).fromStage()).isEqualTo("APPLIED");
        assertThat(result.get(0).rate()).isEqualTo(0.0);
    }
}
