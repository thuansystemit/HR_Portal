package com.demo.app.cv.repository;

import com.demo.app.cv.entity.CvLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CvLanguageRepository extends JpaRepository<CvLanguage, UUID> {

    List<CvLanguage> findByCvCandidateId(UUID cvCandidateId);
}
