package com.demo.app.iam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@IdClass(UserRoleId.class)
public class UserRole {

    @Id
    @Column(nullable = false)
    private UUID userId;

    @Id
    @Column(nullable = false)
    private UUID roleId;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant grantedAt = Instant.now();

    private UUID grantedBy;
}
