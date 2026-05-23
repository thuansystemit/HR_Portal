package com.demo.app.insights.service;

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
class ReportServiceTest {

    @Mock EntityManager em;
    @Mock Query query;

    @InjectMocks
    ReportService reportService;

    /**
     * Build a List<Object[]> that avoids varargs spreading.
     * When passing an Object[] to List.of(T...), Java spreads it, so we use
     * an ArrayList to avoid this ambiguity.
     */
    private static List<Object[]> rowList(Object[]... rows) {
        var list = new ArrayList<Object[]>();
        for (var row : rows) {
            list.add(row);
        }
        return list;
    }

    @Test
    void uploadTrend_mapsRowsCorrectly() {
        Object[] row = new Object[]{"HR Docs", "2024-01", 42};
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rowList(row));

        var result = reportService.uploadTrend();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoryName()).isEqualTo("HR Docs");
        assertThat(result.get(0).month()).isEqualTo("2024-01");
        assertThat(result.get(0).count()).isEqualTo(42);
    }

    @Test
    void uploadTrend_returnsEmptyList() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(new ArrayList<>());

        var result = reportService.uploadTrend();

        assertThat(result).isEmpty();
    }

    @Test
    void categoryCount_mapsRowsCorrectly() {
        Object[] row = new Object[]{"Resumes", 15};
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rowList(row));

        var result = reportService.categoryCount();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoryName()).isEqualTo("Resumes");
        assertThat(result.get(0).count()).isEqualTo(15);
    }

    @Test
    void storageByCategory_mapsRowsCorrectly() {
        Object[] row = new Object[]{"Contracts", 10485760L};
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rowList(row));

        var result = reportService.storageByCategory();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoryName()).isEqualTo("Contracts");
        assertThat(result.get(0).totalBytes()).isEqualTo(10485760L);
    }

    @Test
    void roleDistribution_mapsRowsCorrectly() {
        Object[] row = new Object[]{"Admin", 3};
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(rowList(row));

        var result = reportService.roleDistribution();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roleName()).isEqualTo("Admin");
        assertThat(result.get(0).userCount()).isEqualTo(3);
    }

    @Test
    void refreshMaterializedViews_executesQueries() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        reportService.refreshMaterializedViews();

        verify(em, times(2)).createNativeQuery(anyString());
        verify(query, times(2)).executeUpdate();
    }
}
