package com.demo.app.iam.repository;

import com.demo.app.iam.entity.UserRole;
import com.demo.app.iam.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(UUID userId);

    Optional<UserRole> findByUserIdAndRoleId(UUID userId, UUID roleId);

    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.roleId = :roleId")
    long countByRoleId(UUID roleId);

    void deleteByUserId(UUID userId);
}
