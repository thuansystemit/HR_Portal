package com.demo.app.iam.repository;

import com.demo.app.iam.entity.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByUserId(UUID userId);
}
