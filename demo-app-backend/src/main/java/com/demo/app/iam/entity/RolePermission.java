package com.demo.app.iam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "role_permissions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@IdClass(RolePermissionId.class)
public class RolePermission {

    @Id
    @Column(nullable = false)
    private UUID roleId;

    @Id
    @Column(nullable = false)
    private UUID permissionId;
}
