package com.demo.app.personal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserSettings {

    @Id
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String theme = "system";

    @Column(nullable = false, length = 10)
    private String language = "en";

    @Column(nullable = false, length = 30)
    private String dateFormat = "MM/dd/yyyy";

    @Column(nullable = false)
    private int defaultPageSize = 10;

    @Column(nullable = false)
    private boolean notifEmail = true;

    @Column(nullable = false)
    private boolean notifPush = false;

    @Column(nullable = false)
    private boolean notifDesktop = false;

    @Builder.Default
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
