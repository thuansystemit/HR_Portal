package com.demo.app.iam.repository;

import com.demo.app.iam.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL")
    Page<User> findAllActive(Pageable pageable);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
}
