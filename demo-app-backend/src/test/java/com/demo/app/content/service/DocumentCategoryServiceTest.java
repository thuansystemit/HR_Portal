package com.demo.app.content.service;

import com.demo.app.content.dto.CreateCategoryRequest;
import com.demo.app.content.dto.UpdateCategoryRequest;
import com.demo.app.content.dto.UpdateCategoryRequest.PermissionEntry;
import com.demo.app.content.entity.CategoryRoleVisibility;
import com.demo.app.content.entity.DocumentCategory;
import com.demo.app.content.entity.DocumentType;
import com.demo.app.content.repository.CategoryRoleVisibilityRepository;
import com.demo.app.content.repository.DocumentCategoryRepository;
import com.demo.app.iam.repository.RoleRepository;
import com.demo.app.platform.exception.ConflictException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentCategoryServiceTest {

    @Mock DocumentCategoryRepository categoryRepository;
    @Mock CategoryRoleVisibilityRepository visibilityRepository;
    @Mock RoleRepository roleRepository;

    @InjectMocks
    DocumentCategoryService categoryService;

    private final UUID CAT_ID  = UUID.randomUUID();
    private final UUID ROLE_ID = UUID.randomUUID();

    @Test
    void listForRole_filtersOnCanView() {
        var cat = DocumentCategory.builder().id(CAT_ID).name("Contracts")
                .documentType(DocumentType.INVOICE).documentCount(0).createdAt(Instant.now()).build();
        var vis = CategoryRoleVisibility.builder()
                .categoryId(CAT_ID).roleId(ROLE_ID).canView(true).build();

        when(categoryRepository.findAllActive()).thenReturn(List.of(cat));
        when(visibilityRepository.findByCategoryIdAndRoleId(CAT_ID, ROLE_ID))
                .thenReturn(Optional.of(vis));
        when(visibilityRepository.findByCategoryId(CAT_ID)).thenReturn(List.of(vis));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        var result = categoryService.listForRole(ROLE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Contracts");
    }

    @Test
    void listForRole_excludes_whenCanViewFalse() {
        var cat = DocumentCategory.builder().id(CAT_ID).name("Secret")
                .documentType(DocumentType.CV).build();
        var vis = CategoryRoleVisibility.builder()
                .categoryId(CAT_ID).roleId(ROLE_ID).canView(false).build();

        when(categoryRepository.findAllActive()).thenReturn(List.of(cat));
        when(visibilityRepository.findByCategoryIdAndRoleId(CAT_ID, ROLE_ID))
                .thenReturn(Optional.of(vis));

        var result = categoryService.listForRole(ROLE_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void create_succeeds_whenNameAvailable() {
        var req = new CreateCategoryRequest("Contracts", "Legal docs", DocumentType.INVOICE, null, null);
        var cat = DocumentCategory.builder().id(CAT_ID).name("Contracts")
                .documentType(DocumentType.INVOICE).documentCount(0).createdAt(Instant.now()).build();

        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Contracts"))
                .thenReturn(false);
        when(categoryRepository.save(any())).thenReturn(cat);
        when(visibilityRepository.findByCategoryId(CAT_ID)).thenReturn(List.of());

        var result = categoryService.create(req);

        assertThat(result.name()).isEqualTo("Contracts");
        assertThat(result.documentType()).isEqualTo(DocumentType.INVOICE);
    }

    @Test
    void create_throws_whenNameExists() {
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Contracts"))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(
                new CreateCategoryRequest("Contracts", null, DocumentType.CV, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_savesDocumentType() {
        var cat = DocumentCategory.builder().id(CAT_ID).name("HR Docs")
                .documentType(DocumentType.INVOICE).documentCount(0).createdAt(Instant.now()).build();
        var req = new UpdateCategoryRequest("HR Docs", "Updated", DocumentType.CV, null, null);

        when(categoryRepository.findByIdAndDeletedAtIsNull(CAT_ID)).thenReturn(Optional.of(cat));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(visibilityRepository.findByCategoryId(CAT_ID)).thenReturn(List.of());

        var result = categoryService.update(CAT_ID, req);

        assertThat(result.documentType()).isEqualTo(DocumentType.CV);
        verify(categoryRepository).save(cat);
        assertThat(cat.getDocumentType()).isEqualTo(DocumentType.CV);
    }

    @Test
    void delete_softDeletes_category() {
        var cat = DocumentCategory.builder().id(CAT_ID).name("Test")
                .documentType(DocumentType.INVOICE).build();
        when(categoryRepository.findByIdAndDeletedAtIsNull(CAT_ID)).thenReturn(Optional.of(cat));
        when(categoryRepository.save(any())).thenReturn(cat);

        categoryService.delete(CAT_ID);

        assertThat(cat.getDeletedAt()).isNotNull();
    }

    @Test
    void findById_throws_whenNotFound() {
        when(categoryRepository.findByIdAndDeletedAtIsNull(CAT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findById(CAT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
