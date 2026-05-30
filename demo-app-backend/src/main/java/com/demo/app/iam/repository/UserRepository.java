package com.demo.app.iam.repository;

import com.demo.app.iam.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL")
    Page<User> findAllActive(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND u.id IN " +
           "(SELECT ur.userId FROM UserRole ur WHERE ur.roleId = :roleId)")
    List<User> findActiveByRoleId(@Param("roleId") UUID roleId);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    // IA-4(e): active accounts whose last login (or creation, for never-logged-in accounts) predates cutoff
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND u.status = 'active' " +
           "AND u.createdAt < :cutoff " +
           "AND EXISTS (SELECT 1 FROM Credential c WHERE c.userId = u.id " +
           "AND (c.lastLoginAt IS NULL OR c.lastLoginAt < :cutoff))")
    List<User> findActiveUsersInactiveSince(@Param("cutoff") Instant cutoff);
}
