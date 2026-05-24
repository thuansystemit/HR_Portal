package com.demo.app.recruitment.repository;

import com.demo.app.recruitment.entity.JobPosting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    Page<JobPosting> findByStatus(String status, Pageable pageable);

    Page<JobPosting> findAll(Pageable pageable);
}
