package com.demo.app.cv.repository;

import com.demo.app.cv.entity.CvCandidate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvCandidateRepository extends JpaRepository<CvCandidate, UUID> {

    Optional<CvCandidate> findByDocumentId(UUID documentId);

    List<CvCandidate> findByDocumentCategoryId(UUID categoryId);

    boolean existsByDocumentId(UUID documentId);

    @Query("SELECT c FROM CvCandidate c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<CvCandidate> searchSimple(@Param("q") String q, Pageable pageable);
}
