package com.demo.app.recruitment.repository;

import com.demo.app.recruitment.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findByApplicationIdOrderByScheduledAtAsc(UUID applicationId);
}
