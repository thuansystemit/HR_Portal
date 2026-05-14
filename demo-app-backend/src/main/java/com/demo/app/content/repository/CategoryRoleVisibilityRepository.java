package com.demo.app.content.repository;

import com.demo.app.content.entity.CategoryRoleVisibility;
import com.demo.app.content.entity.CategoryRoleVisibilityId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRoleVisibilityRepository
        extends JpaRepository<CategoryRoleVisibility, CategoryRoleVisibilityId> {

    List<CategoryRoleVisibility> findByCategoryId(UUID categoryId);

    Optional<CategoryRoleVisibility> findByCategoryIdAndRoleId(UUID categoryId, UUID roleId);
}
