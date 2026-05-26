package com.demo.app.recruitment.service;

import com.demo.app.cv.repository.CvCandidateRepository;
import com.demo.app.cv.repository.CvTechnicalSkillRepository;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.recruitment.dto.*;
import com.demo.app.recruitment.entity.ApplicationStageHistory;
import com.demo.app.recruitment.entity.JobApplication;
import com.demo.app.recruitment.repository.ApplicationStageHistoryRepository;
import com.demo.app.recruitment.repository.JobApplicationRepository;
import com.demo.app.recruitment.repository.JobPostingRepository;
import com.demo.app.recruitment.repository.JobPostingSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class JobApplicationService {

    private static final List<String> VALID_STAGES =
            List.of("APPLIED", "SCREENING", "INTERVIEW", "OFFER", "HIRED", "REJECTED");

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationStageHistoryRepository stageHistoryRepository;
    private final CvCandidateRepository cvCandidateRepository;
    private final CandidateHiringStatusService hiringStatusService;
    private final JobPostingSkillRepository jobPostingSkillRepository;
    private final CvTechnicalSkillRepository cvTechnicalSkillRepository;

    public ApplicationResponse apply(UUID jobPostingId, CreateApplicationRequest req, UUID moverId) {
        var posting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("JobPosting", jobPostingId));

        if (!"OPEN".equals(posting.getStatus())) {
            throw new IllegalStateException("Job posting is not open for applications");
        }

        if (jobApplicationRepository.existsByJobPostingIdAndCvCandidateId(jobPostingId, req.cvCandidateId())) {
            throw new ConflictException("Candidate has already applied to this job posting");
        }

        var application = JobApplication.builder()
                .jobPostingId(jobPostingId)
                .cvCandidateId(req.cvCandidateId())
                .stage("APPLIED")
                .notes(req.notes())
                .build();
        var saved = jobApplicationRepository.save(application);

        stageHistoryRepository.save(ApplicationStageHistory.builder()
                .applicationId(saved.getId())
                .fromStage(null)
                .toStage("APPLIED")
                .movedBy(moverId)
                .notes(req.notes())
                .build());

        hiringStatusService.recalculate(req.cvCandidateId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listByPosting(UUID jobPostingId) {
        return jobApplicationRepository.findByJobPostingId(jobPostingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoard(UUID jobPostingId) {
        var applications = jobApplicationRepository.findByJobPostingId(jobPostingId);
        Map<String, List<ApplicationResponse>> columns = new LinkedHashMap<>();
        for (String stage : VALID_STAGES) {
            columns.put(stage, new ArrayList<>());
        }
        for (var app : applications) {
            var stage = app.getStage();
            columns.computeIfAbsent(stage, k -> new ArrayList<>()).add(toResponse(app));
        }
        return new BoardResponse(columns);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listByCandidateId(UUID cvCandidateId) {
        return jobApplicationRepository.findByCvCandidateId(cvCandidateId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ApplicationResponse moveStage(UUID jobPostingId, UUID appId, MoveStageRequest req, UUID moverId) {
        if (!VALID_STAGES.contains(req.stage())) {
            throw new IllegalArgumentException("Invalid stage: " + req.stage());
        }

        var application = jobApplicationRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("JobApplication", appId));

        if (!application.getJobPostingId().equals(jobPostingId)) {
            throw new ResourceNotFoundException("JobApplication", appId);
        }

        var fromStage = application.getStage();
        application.setStage(req.stage());
        var saved = jobApplicationRepository.save(application);

        stageHistoryRepository.save(ApplicationStageHistory.builder()
                .applicationId(appId)
                .fromStage(fromStage)
                .toStage(req.stage())
                .movedBy(moverId)
                .notes(req.notes())
                .build());

        hiringStatusService.recalculate(application.getCvCandidateId());
        return toResponse(saved);
    }

    private ApplicationResponse toResponse(JobApplication app) {
        var candidate = cvCandidateRepository.findById(app.getCvCandidateId()).orElse(null);
        String fullName           = candidate != null ? candidate.getFullName()           : null;
        String email              = candidate != null ? candidate.getEmail()              : null;
        UUID   documentCategoryId = candidate != null ? candidate.getDocumentCategoryId() : null;
        var    posting            = jobPostingRepository.findById(app.getJobPostingId()).orElse(null);
        String jobTitle           = posting != null ? posting.getTitle() : null;
        Integer fitScore          = computeFitScore(app.getJobPostingId(), app.getCvCandidateId());
        return new ApplicationResponse(
                app.getId(), app.getJobPostingId(), jobTitle, app.getCvCandidateId(),
                documentCategoryId, fullName, email, app.getStage(), app.getNotes(),
                app.getAppliedAt(), app.getUpdatedAt(), fitScore);
    }

    private Integer computeFitScore(UUID jobPostingId, UUID cvCandidateId) {
        var required = jobPostingSkillRepository.findByJobPostingId(jobPostingId);
        if (required.isEmpty()) return null;
        var candidateSkills = cvTechnicalSkillRepository.findByCvCandidateId(cvCandidateId)
                .stream()
                .map(s -> s.getSkillName().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());
        long matched = required.stream()
                .filter(s -> candidateSkills.contains(s.getSkillName().toLowerCase()))
                .count();
        return (int) Math.round(matched * 100.0 / required.size());
    }
}
