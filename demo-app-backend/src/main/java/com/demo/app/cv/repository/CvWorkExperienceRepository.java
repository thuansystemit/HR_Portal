package com.demo.app.cv.repository;

import com.demo.app.cv.entity.CvWorkExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CvWorkExperienceRepository extends JpaRepository<CvWorkExperience, UUID> {

    List<CvWorkExperience> findByCvCandidateIdOrderBySortOrder(UUID cvCandidateId);
}
