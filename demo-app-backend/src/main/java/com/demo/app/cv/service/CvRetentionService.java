package com.demo.app.cv.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.cv.repository.CvCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * SI-12: Enforces data-retention policy for CV candidate records.
 * Candidates that have exceeded the retention window and are not in an active
 * hiring process have their PII fields anonymised in-place rather than hard-deleted,
 * preserving referential integrity while removing personal data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CvRetentionService {

    private final CvCandidateRepository cvCandidateRepository;
    private final AuditService auditService;

    @Value("${app.data.retention.cv-days:1095}")
    private int cvRetentionDays = 1095;

    @Scheduled(cron = "0 0 1 1 * ?")
    @Transactional
    public void anonymizeExpiredCandidates() {
        if (cvRetentionDays <= 0) return;

        var cutoff = Instant.now().minusSeconds((long) cvRetentionDays * 86_400);
        var candidates = cvCandidateRepository.findAnonymizableBefore(cutoff);
        if (candidates.isEmpty()) return;

        for (var candidate : candidates) {
            candidate.setFullName("ANONYMIZED");
            candidate.setEmail(null);
            candidate.setPhone(null);
            candidate.setCity(null);
            candidate.setCountry(null);
            candidate.setLinkedinUrl(null);
            candidate.setGithubUrl(null);
            candidate.setPortfolioUrl(null);
            candidate.setSummary(null);
            candidate.setAnonymizedAt(Instant.now());
            cvCandidateRepository.save(candidate);
        }

        log.info("SI-12: anonymized {} CV candidates past retention cutoff ({} days)", candidates.size(), cvRetentionDays);
        auditService.log(null, "CV_BATCH_ANONYMIZED", "CvCandidate", null, null,
                Map.of("count", String.valueOf(candidates.size()),
                        "cutoffDays", String.valueOf(cvRetentionDays),
                        "cutoffInstant", cutoff.toString()),
                "success");
    }
}
