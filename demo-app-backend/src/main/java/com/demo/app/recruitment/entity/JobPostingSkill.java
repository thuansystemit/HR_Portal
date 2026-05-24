package com.demo.app.recruitment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "job_posting_skills")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostingSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID jobPostingId;

    @Column(nullable = false, length = 100)
    private String skillName;

    @Builder.Default
    @Column(name = "is_required", nullable = false)
    private boolean isRequired = true;
}
