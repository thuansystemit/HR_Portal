package com.demo.app.recruitment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_feedback")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID interviewId;

    @Column(nullable = false)
    private UUID reviewerId;

    @Column(nullable = false)
    private short rating;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, length = 10)
    private String recommendation;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();
}
