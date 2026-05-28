package com.demo.app.iam.repository;

import com.demo.app.iam.entity.MfaPendingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MfaPendingSessionRepository extends JpaRepository<MfaPendingSession, UUID> {

    Optional<MfaPendingSession> findByChallengeToken(String challengeToken);

    @Modifying
    @Query("DELETE FROM MfaPendingSession s WHERE s.expiresAt < :cutoff")
    void deleteExpired(Instant cutoff);
}
