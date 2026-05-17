package com.demo.app.content.service;

import com.demo.app.content.dto.CategoryPermissionDto;
import com.demo.app.content.dto.CategoryResponse;
import com.demo.app.content.dto.CreateCategoryRequest;
import com.demo.app.content.dto.UpdateCategoryRequest;
import com.demo.app.content.entity.CategoryRoleVisibility;
import com.demo.app.content.entity.DocumentCategory;
import com.demo.app.content.repository.CategoryRoleVisibilityRepository;
import com.demo.app.content.repository.DocumentCategoryRepository;
import com.demo.app.iam.repository.RoleRepository;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentCategoryService {

    private final DocumentCategoryRepository categoryRepository;
    private final CategoryRoleVisibilityRepository visibilityRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listForRole(UUID roleId) {
        return categoryRepository.findAllActive().stream()
                .filter(c -> roleId == null || isVisible(c.getId(), roleId))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        var cat = categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentCategory", id));
        return toResponse(cat);
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(request.name())) {
            throw new ConflictException("Category name already in use: " + request.name());
        }
        var cat = DocumentCategory.builder()
                .name(request.name())
                .description(request.description())
                .documentType(request.documentType())
                .llmExtraction(request.llmExtraction() == null || request.llmExtraction())
                .build();
        var saved = categoryRepository.save(cat);

        if (request.permissions() != null) {
            for (var entry : request.permissions()) {
                var vis = CategoryRoleVisibility.builder()
                        .categoryId(saved.getId()).roleId(entry.roleId()).build();
                vis.setCanView(entry.canView());
                vis.setCanUpload(entry.canUpload());
                vis.setCanDelete(entry.canDelete());
                visibilityRepository.save(vis);
            }
        }

        return toResponse(saved);
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        var cat = categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentCategory", id));
        cat.setName(request.name());
        cat.setDescription(request.description());
        cat.setDocumentType(request.documentType());
        if (request.llmExtraction() != null) cat.setLlmExtraction(request.llmExtraction());
        categoryRepository.save(cat);

        if (request.permissions() != null) {
            for (var entry : request.permissions()) {
                var vis = visibilityRepository
                        .findByCategoryIdAndRoleId(id, entry.roleId())
                        .orElseGet(() -> CategoryRoleVisibility.builder()
                                .categoryId(id).roleId(entry.roleId()).build());
                vis.setCanView(entry.canView());
                vis.setCanUpload(entry.canUpload());
                vis.setCanDelete(entry.canDelete());
                visibilityRepository.save(vis);
            }
        }

        return toResponse(cat);
    }

    @Transactional
    public void delete(UUID id) {
        var cat = categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentCategory", id));
        cat.setDeletedAt(Instant.now());
        categoryRepository.save(cat);
    }

    private boolean isVisible(UUID categoryId, UUID roleId) {
        return visibilityRepository.findByCategoryIdAndRoleId(categoryId, roleId)
                .map(CategoryRoleVisibility::isCanView)
                .orElse(false);
    }

    private CategoryResponse toResponse(DocumentCategory cat) {
        var perms = visibilityRepository.findByCategoryId(cat.getId()).stream()
                .map(v -> {
                    var roleName = roleRepository.findById(v.getRoleId())
                            .map(r -> r.getName()).orElse("unknown");
                    return new CategoryPermissionDto(v.getRoleId(), roleName,
                            v.isCanView(), v.isCanUpload(), v.isCanDelete());
                })
                .collect(Collectors.toList());
        return new CategoryResponse(cat.getId(), cat.getName(), cat.getDescription(),
                cat.getDocumentCount(), perms, cat.getCreatedAt(), cat.getDocumentType(),
                cat.isLlmExtraction());
    }
}
