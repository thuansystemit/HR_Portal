package com.demo.app.cv.repository;

import com.demo.app.cv.entity.CvCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvCandidateRepository extends JpaRepository<CvCandidate, UUID> {

    Optional<CvCandidate> findByDocumentId(UUID documentId);

    List<CvCandidate> findByDocumentCategoryId(UUID categoryId);

    boolean existsByDocumentId(UUID documentId);
}
