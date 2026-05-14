package com.demo.app.content.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "category_role_visibility")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@IdClass(CategoryRoleVisibilityId.class)
public class CategoryRoleVisibility {

    @Id
    @Column(nullable = false)
    private UUID categoryId;

    @Id
    @Column(nullable = false)
    private UUID roleId;

    @Column(nullable = false)
    private boolean canView = false;

    @Column(nullable = false)
    private boolean canUpload = false;

    @Column(nullable = false)
    private boolean canDelete = false;
}
