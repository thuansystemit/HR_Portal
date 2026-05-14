package com.demo.app.iam.repository;

import com.demo.app.iam.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    void revokeAllForUser(UUID userId, Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteExpired(Instant now);
}
