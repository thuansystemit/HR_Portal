package com.demo.app.recruitment.service;

import com.demo.app.cv.entity.CandidateHiringStatus;
import com.demo.app.cv.repository.CvCandidateRepository;
import com.demo.app.recruitment.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CandidateHiringStatusService {

    private final CvCandidateRepository candidateRepository;
    private final JobApplicationRepository applicationRepository;

    /**
     * Recalculates and persists the hiring_status for a candidate based on
     * the highest-priority stage across all their job applications.
     * Priority: HIRED > OFFERED > IN_PROCESS > REJECTED > AVAILABLE
     */
    public void recalculate(UUID cvCandidateId) {
        var candidate = candidateRepository.findById(cvCandidateId).orElse(null);
        if (candidate == null) return;

        Set<String> stages = applicationRepository.findByCvCandidateId(cvCandidateId).stream()
                .map(a -> a.getStage())
                .collect(Collectors.toSet());

        CandidateHiringStatus status;
        if (stages.contains("HIRED")) {
            status = CandidateHiringStatus.HIRED;
        } else if (stages.contains("OFFER")) {
            status = CandidateHiringStatus.OFFERED;
        } else if (stages.stream().anyMatch(s -> Set.of("INTERVIEW", "SCREENING", "APPLIED").contains(s))) {
            status = CandidateHiringStatus.IN_PROCESS;
        } else if (!stages.isEmpty()) {
            status = CandidateHiringStatus.REJECTED;
        } else {
            status = CandidateHiringStatus.AVAILABLE;
        }

        candidate.setHiringStatus(status);
        candidateRepository.save(candidate);
    }
}
