package com.demo.app.hiring.repository;

import com.demo.app.hiring.entity.CvShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvShareRepository extends JpaRepository<CvShare, UUID> {

    List<CvShare> findByHiringRequestIdOrderBySharedAtDesc(UUID hiringRequestId);

    List<CvShare> findBySharedWithOrderBySharedAtDesc(UUID sharedWith);

    Optional<CvShare> findByHiringRequestIdAndCvCandidateIdAndSharedWith(
            UUID hiringRequestId, UUID cvCandidateId, UUID sharedWith);

    boolean existsByHiringRequestIdAndCvCandidateIdAndSharedWith(
            UUID hiringRequestId, UUID cvCandidateId, UUID sharedWith);
}
