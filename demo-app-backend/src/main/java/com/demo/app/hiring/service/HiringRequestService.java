package com.demo.app.hiring.service;

import com.demo.app.hiring.dto.CreateHiringRequestRequest;
import com.demo.app.hiring.dto.HiringRequestResponse;
import com.demo.app.hiring.dto.UpdateHiringRequestStatusRequest;
import com.demo.app.hiring.entity.HiringRequest;
import com.demo.app.hiring.repository.HiringRequestRepository;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.platform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class HiringRequestService {

    private static final List<String> VALID_ROLE_TYPES = List.of("FRONTEND", "BACKEND", "FULLSTACK");
    private static final List<String> VALID_URGENCIES  = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final List<String> VALID_STATUSES   = List.of(
            "PENDING", "IN_PROGRESS", "CANDIDATE_FOUND", "HIRED", "CLOSED", "REJECTED");

    private final HiringRequestRepository hiringRequestRepository;
    private final UserRepository userRepository;

    public HiringRequestResponse create(CreateHiringRequestRequest req, UUID requesterId) {
        validateRoleType(req.roleType());
        String urgency = req.urgency() != null ? req.urgency() : "MEDIUM";
        validateUrgency(urgency);

        HiringRequest entity = HiringRequest.builder()
                .requesterId(requesterId)
                .title(req.title())
                .description(req.description())
                .roleType(req.roleType())
                .department(req.department())
                .urgency(urgency)
                .build();
        return toResponse(hiringRequestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<HiringRequestResponse> listByRequester(UUID requesterId) {
        return hiringRequestRepository.findByRequesterIdOrderByCreatedAtDesc(requesterId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HiringRequestResponse> listAll() {
        return hiringRequestRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public HiringRequestResponse updateStatus(UUID id, UpdateHiringRequestStatusRequest req, UUID updatedBy) {
        validateStatus(req.status());

        HiringRequest entity = hiringRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HiringRequest", id));
        entity.setStatus(req.status());
        if (req.jobPostingId() != null) {
            entity.setJobPostingId(req.jobPostingId());
        }
        return toResponse(hiringRequestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public HiringRequestResponse getById(UUID id) {
        return hiringRequestRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("HiringRequest", id));
    }

    private HiringRequestResponse toResponse(HiringRequest entity) {
        String requesterName = userRepository.findByIdAndDeletedAtIsNull(entity.getRequesterId())
                .map(u -> u.getFullName())
                .orElse(null);
        return new HiringRequestResponse(
                entity.getId(),
                entity.getRequesterId(),
                requesterName,
                entity.getTitle(),
                entity.getDescription(),
                entity.getRoleType(),
                entity.getDepartment(),
                entity.getUrgency(),
                entity.getStatus(),
                entity.getJobPostingId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validateRoleType(String roleType) {
        if (!VALID_ROLE_TYPES.contains(roleType)) {
            throw new IllegalArgumentException(
                    "Invalid roleType '" + roleType + "'. Allowed values: " + VALID_ROLE_TYPES);
        }
    }

    private void validateUrgency(String urgency) {
        if (!VALID_URGENCIES.contains(urgency)) {
            throw new IllegalArgumentException(
                    "Invalid urgency '" + urgency + "'. Allowed values: " + VALID_URGENCIES);
        }
    }

    private void validateStatus(String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "Invalid status '" + status + "'. Allowed values: " + VALID_STATUSES);
        }
    }
}
