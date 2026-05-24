package com.demo.app.recruitment.repository;

import com.demo.app.recruitment.entity.ApplicationStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationStageHistoryRepository extends JpaRepository<ApplicationStageHistory, UUID> {

    List<ApplicationStageHistory> findByApplicationIdOrderByMovedAtAsc(UUID applicationId);
}
