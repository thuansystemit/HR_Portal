package com.demo.app.iam.repository;

import com.demo.app.iam.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Role> findByName(String name);
}
