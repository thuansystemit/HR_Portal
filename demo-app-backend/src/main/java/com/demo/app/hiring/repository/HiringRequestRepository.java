package com.demo.app.hiring.repository;

import com.demo.app.hiring.entity.HiringRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HiringRequestRepository extends JpaRepository<HiringRequest, UUID> {

    List<HiringRequest> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    List<HiringRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<HiringRequest> findAllByOrderByCreatedAtDesc();
}
