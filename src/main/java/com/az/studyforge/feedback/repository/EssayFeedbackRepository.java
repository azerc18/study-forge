package com.az.studyforge.feedback.repository;

import com.az.studyforge.feedback.entity.EssayFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EssayFeedbackRepository extends JpaRepository<EssayFeedback, UUID> {
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant from);
    List<EssayFeedback> findTop20ByQuestionIdAndUserIdOrderByCreatedAtDesc(UUID questionId, UUID userId);
}
