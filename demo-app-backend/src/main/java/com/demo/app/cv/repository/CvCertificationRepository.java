package com.demo.app.cv.repository;

import com.demo.app.cv.entity.CvCertification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CvCertificationRepository extends JpaRepository<CvCertification, UUID> {

    List<CvCertification> findByCvCandidateId(UUID cvCandidateId);
}
