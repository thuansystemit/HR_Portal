package com.demo.app.iam.repository;

import com.demo.app.iam.entity.RolePermission;
import com.demo.app.iam.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByRoleId(UUID roleId);

    void deleteByRoleId(UUID roleId);
}
