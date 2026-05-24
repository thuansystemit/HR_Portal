package com.demo.app.recruitment.service;

import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.recruitment.dto.*;
import com.demo.app.recruitment.entity.JobPosting;
import com.demo.app.recruitment.entity.JobPostingSkill;
import com.demo.app.recruitment.repository.JobApplicationRepository;
import com.demo.app.recruitment.repository.JobPostingRepository;
import com.demo.app.recruitment.repository.JobPostingSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingSkillRepository jobPostingSkillRepository;

    public JobPostingResponse create(CreateJobPostingRequest req, UUID createdBy) {
        var posting = JobPosting.builder()
                .title(req.title())
                .department(req.department())
                .location(req.location())
                .description(req.description())
                .requirements(req.requirements())
                .deadline(req.deadline())
                .status(req.status() != null ? req.status() : "DRAFT")
                .createdBy(createdBy)
                .build();
        var saved = jobPostingRepository.save(posting);
        return toResponse(saved, 0);
    }

    @Transactional(readOnly = true)
    public Page<JobPostingSummary> list(String status, Pageable pageable) {
        Page<JobPosting> page = (status != null && !status.isBlank())
                ? jobPostingRepository.findByStatus(status, pageable)
                : jobPostingRepository.findAll(pageable);
        return page.map(p -> toSummary(p, jobApplicationRepository.countByJobPostingId(p.getId())));
    }

    @Transactional(readOnly = true)
    public JobPostingResponse findById(UUID id) {
        var posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobPosting", id));
        int count = jobApplicationRepository.countByJobPostingId(id);
        return toResponse(posting, count);
    }

    public JobPostingResponse update(UUID id, UpdateJobPostingRequest req) {
        var posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobPosting", id));
        if (req.title() != null)        posting.setTitle(req.title());
        if (req.department() != null)   posting.setDepartment(req.department());
        if (req.location() != null)     posting.setLocation(req.location());
        if (req.description() != null)  posting.setDescription(req.description());
        if (req.requirements() != null) posting.setRequirements(req.requirements());
        if (req.deadline() != null)     posting.setDeadline(req.deadline());
        if (req.status() != null)       posting.setStatus(req.status());
        var saved = jobPostingRepository.save(posting);
        int count = jobApplicationRepository.countByJobPostingId(id);
        return toResponse(saved, count);
    }

    public void delete(UUID id) {
        var posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobPosting", id));
        posting.setStatus("CLOSED");
        jobPostingRepository.save(posting);
    }

    private JobPostingResponse toResponse(JobPosting p, int applicationCount) {
        return new JobPostingResponse(
                p.getId(), p.getTitle(), p.getDepartment(), p.getLocation(),
                p.getDescription(), p.getRequirements(), p.getDeadline(), p.getStatus(),
                p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedAt(), applicationCount);
    }

    private JobPostingSummary toSummary(JobPosting p, int applicationCount) {
        return new JobPostingSummary(
                p.getId(), p.getTitle(), p.getDepartment(), p.getLocation(),
                p.getStatus(), p.getDeadline(), applicationCount);
    }

    @Transactional(readOnly = true)
    public List<JobPostingSkillDto> getSkills(UUID jobPostingId) {
        jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("JobPosting", jobPostingId));
        return jobPostingSkillRepository.findByJobPostingId(jobPostingId)
                .stream()
                .map(s -> new JobPostingSkillDto(s.getSkillName(), s.isRequired()))
                .toList();
    }

    public List<JobPostingSkillDto> setSkills(UUID jobPostingId, List<JobPostingSkillDto> skills) {
        jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("JobPosting", jobPostingId));
        jobPostingSkillRepository.deleteByJobPostingId(jobPostingId);
        List<JobPostingSkill> entities = skills.stream()
                .map(dto -> JobPostingSkill.builder()
                        .jobPostingId(jobPostingId)
                        .skillName(dto.skillName())
                        .isRequired(dto.isRequired())
                        .build())
                .toList();
        return jobPostingSkillRepository.saveAll(entities)
                .stream()
                .map(s -> new JobPostingSkillDto(s.getSkillName(), s.isRequired()))
                .toList();
    }
}
