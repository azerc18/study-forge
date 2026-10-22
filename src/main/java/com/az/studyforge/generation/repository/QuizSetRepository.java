package com.az.studyforge.generation.repository;

import com.az.studyforge.generation.entity.QuizSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface QuizSetRepository extends JpaRepository<QuizSet, UUID> {
    Optional<QuizSet> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant from);
}
