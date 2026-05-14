package com.demo.app.cv.repository;

import com.demo.app.cv.entity.CvTechnicalSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CvTechnicalSkillRepository extends JpaRepository<CvTechnicalSkill, UUID> {

    List<CvTechnicalSkill> findByCvCandidateId(UUID cvCandidateId);
}
