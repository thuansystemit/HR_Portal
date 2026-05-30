package com.demo.app.cv.repository;

import com.demo.app.cv.entity.CvCandidate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvCandidateRepository extends JpaRepository<CvCandidate, UUID> {

    Optional<CvCandidate> findByDocumentId(UUID documentId);

    List<CvCandidate> findByDocumentCategoryId(UUID categoryId);

    boolean existsByDocumentId(UUID documentId);

    @Query("SELECT c FROM CvCandidate c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<CvCandidate> searchSimple(@Param("q") String q, Pageable pageable);

    // SI-12: candidates eligible for PII anonymisation: past retention cutoff, not in an active
    // hiring process, and not already anonymised
    @Query("SELECT c FROM CvCandidate c WHERE c.createdAt < :cutoff " +
           "AND c.hiringStatus NOT IN " +
           "(com.demo.app.cv.entity.CandidateHiringStatus.IN_PROCESS, " +
           "com.demo.app.cv.entity.CandidateHiringStatus.OFFERED, " +
           "com.demo.app.cv.entity.CandidateHiringStatus.HIRED) " +
           "AND c.anonymizedAt IS NULL")
    List<CvCandidate> findAnonymizableBefore(@Param("cutoff") Instant cutoff);
}
