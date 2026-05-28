package com.demo.app.hiring.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.cv.repository.CvCandidateRepository;
import com.demo.app.hiring.dto.CvShareResponse;
import com.demo.app.hiring.dto.ShareCvRequest;
import com.demo.app.hiring.dto.SubmitImpressionRequest;
import com.demo.app.hiring.entity.CvShare;
import com.demo.app.hiring.entity.HiringRequest;
import com.demo.app.hiring.repository.CvShareRepository;
import com.demo.app.hiring.repository.HiringRequestRepository;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CvShareService {

    private static final List<String> VALID_IMPRESSIONS =
            List.of("INTERESTED", "NOT_INTERESTED", "REVIEW_LATER");

    private final CvShareRepository cvShareRepository;
    private final HiringRequestRepository hiringRequestRepository;
    private final CvCandidateRepository cvCandidateRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public CvShareResponse share(UUID hiringRequestId, ShareCvRequest req, UUID sharedByUserId) {
        hiringRequestRepository.findById(hiringRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("HiringRequest", hiringRequestId));

        if (cvShareRepository.existsByHiringRequestIdAndCvCandidateIdAndSharedWith(
                hiringRequestId, req.cvCandidateId(), req.sharedWith())) {
            throw new ConflictException(
                    "CV already shared with this user for the given hiring request");
        }

        CvShare entity = CvShare.builder()
                .hiringRequestId(hiringRequestId)
                .cvCandidateId(req.cvCandidateId())
                .sharedBy(sharedByUserId)
                .sharedWith(req.sharedWith())
                .comment(req.comment())
                .build();
        var saved = toResponse(cvShareRepository.save(entity));
        auditService.log(sharedByUserId, "CV_SHARED", "CvShare", saved.id(),
                null, Map.of("sharedWith", req.sharedWith().toString()), "success");
        return saved;
    }

    public CvShareResponse submitImpression(UUID shareId, SubmitImpressionRequest req, UUID reviewerId) {
        validateImpression(req.impression());

        CvShare entity = cvShareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("CvShare", shareId));
        entity.setImpression(req.impression());
        entity.setComment(req.comment());
        entity.setReviewedAt(Instant.now());
        return toResponse(cvShareRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<CvShareResponse> listByRequest(UUID hiringRequestId) {
        return cvShareRepository.findByHiringRequestIdOrderBySharedAtDesc(hiringRequestId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CvShareResponse> listMySharedCvs(UUID sharedWith) {
        return cvShareRepository.findBySharedWithOrderBySharedAtDesc(sharedWith)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CvShareResponse getById(UUID shareId) {
        return toResponse(cvShareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("CvShare", shareId)));
    }

    private CvShareResponse toResponse(CvShare entity) {
        String hiringRequestTitle = hiringRequestRepository.findById(entity.getHiringRequestId())
                .map(HiringRequest::getTitle)
                .orElse(null);
        var candidateOpt = cvCandidateRepository.findById(entity.getCvCandidateId());
        String candidateFullName = candidateOpt.map(c -> c.getFullName()).orElse(null);
        String candidateHiringStatus = candidateOpt
                .map(c -> c.getHiringStatus() != null ? c.getHiringStatus().name() : "AVAILABLE")
                .orElse(null);
        String sharedByName = userRepository.findByIdAndDeletedAtIsNull(entity.getSharedBy())
                .map(u -> u.getFullName())
                .orElse(null);
        String sharedWithName = userRepository.findByIdAndDeletedAtIsNull(entity.getSharedWith())
                .map(u -> u.getFullName())
                .orElse(null);
        return new CvShareResponse(
                entity.getId(),
                entity.getHiringRequestId(),
                hiringRequestTitle,
                entity.getCvCandidateId(),
                candidateFullName,
                candidateHiringStatus,
                entity.getSharedBy(),
                sharedByName,
                entity.getSharedWith(),
                sharedWithName,
                entity.getImpression(),
                entity.getComment(),
                entity.getSharedAt(),
                entity.getReviewedAt()
        );
    }

    private void validateImpression(String impression) {
        if (!VALID_IMPRESSIONS.contains(impression)) {
            throw new IllegalArgumentException(
                    "Invalid impression '" + impression + "'. Allowed values: " + VALID_IMPRESSIONS);
        }
    }
}
