package com.demo.app.cv.repository;

import com.demo.app.cv.entity.CvEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CvEducationRepository extends JpaRepository<CvEducation, UUID> {

    List<CvEducation> findByCvCandidateIdOrderBySortOrder(UUID cvCandidateId);
}
