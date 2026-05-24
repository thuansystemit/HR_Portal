package com.demo.app.recruitment.repository;

import com.demo.app.recruitment.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findByJobPostingId(UUID jobPostingId);

    Optional<JobApplication> findByJobPostingIdAndCvCandidateId(UUID jobPostingId, UUID cvCandidateId);

    boolean existsByJobPostingIdAndCvCandidateId(UUID jobPostingId, UUID cvCandidateId);

    int countByJobPostingId(UUID jobPostingId);

    List<JobApplication> findByCvCandidateId(UUID cvCandidateId);
}
