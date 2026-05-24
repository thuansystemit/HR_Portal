package com.demo.app.recruitment.repository;

import com.demo.app.recruitment.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, UUID> {

    List<InterviewFeedback> findByInterviewId(UUID interviewId);

    boolean existsByInterviewIdAndReviewerId(UUID interviewId, UUID reviewerId);
}
