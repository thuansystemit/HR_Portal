package com.demo.app.iam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 60, unique = true)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 40)
    private String category = "general";
}
