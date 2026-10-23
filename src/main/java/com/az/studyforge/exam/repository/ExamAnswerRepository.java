package com.az.studyforge.exam.repository;

import com.az.studyforge.exam.entity.ExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamAnswerRepository extends JpaRepository<ExamAnswer, Long> {
    List<ExamAnswer> findByAttemptIdOrderByOrderIndex(UUID attemptId);
}