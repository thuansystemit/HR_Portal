package com.demo.app.recruitment.repository;

import com.demo.app.recruitment.entity.JobPostingSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobPostingSkillRepository extends JpaRepository<JobPostingSkill, UUID> {

    List<JobPostingSkill> findByJobPostingId(UUID jobPostingId);

    void deleteByJobPostingId(UUID jobPostingId);

    List<JobPostingSkill> findBySkillNameIgnoreCase(String skillName);
}
