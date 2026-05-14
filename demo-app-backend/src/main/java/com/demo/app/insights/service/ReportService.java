package com.demo.app.insights.service;

import com.demo.app.insights.dto.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final EntityManager em;

    @Cacheable(value = "reports", key = "'uploadTrend'", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<UploadTrendEntry> uploadTrend() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT category_name, TO_CHAR(month, 'YYYY-MM'), upload_count " +
                "FROM mv_upload_trend ORDER BY month, category_name")
                .getResultList();
        return rows.stream()
                .map(r -> new UploadTrendEntry((String) r[0], (String) r[1], ((Number) r[2]).intValue()))
                .toList();
    }

    @Cacheable(value = "reports", key = "'categoryCount'", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<CategoryCountEntry> categoryCount() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT category_name, document_count FROM mv_storage_by_category ORDER BY category_name")
                .getResultList();
        return rows.stream()
                .map(r -> new CategoryCountEntry((String) r[0], ((Number) r[1]).intValue()))
                .toList();
    }

    @Cacheable(value = "reports", key = "'storage'", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<StorageEntry> storageByCategory() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT category_name, total_bytes FROM mv_storage_by_category ORDER BY total_bytes DESC")
                .getResultList();
        return rows.stream()
                .map(r -> new StorageEntry((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDistributionEntry> roleDistribution() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT r.name, COUNT(ur.user_id) " +
                "FROM roles r LEFT JOIN user_roles ur ON ur.role_id = r.id " +
                "GROUP BY r.name ORDER BY r.name")
                .getResultList();
        return rows.stream()
                .map(r -> new RoleDistributionEntry((String) r[0], ((Number) r[1]).intValue()))
                .toList();
    }

    @Scheduled(cron = "0 0 2 * * *")
    @CacheEvict(value = "reports", allEntries = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshMaterializedViews() {
        log.info("Refreshing materialized views");
        em.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_upload_trend").executeUpdate();
        em.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_storage_by_category").executeUpdate();
    }
}
