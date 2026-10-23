package com.az.studyforge.exam.repository;

import com.az.studyforge.exam.entity.ExamAttempt;
import com.az.studyforge.exam.entity.ExamStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    Optional<ExamAttempt> findByIdAndUserId(UUID id, UUID userId);

    Optional<ExamAttempt> findByUserIdAndQuizSetIdAndStatus(UUID userId, UUID quizSetId, ExamStatus status);

    List<ExamAttempt> findTop20ByUserIdAndQuizSetIdOrderByStartedAtDesc(UUID userId, UUID quizSetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.id = :id and a.userId = :userId")
    Optional<ExamAttempt> findForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}