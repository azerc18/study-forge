package com.az.studyforge.generation.repository;

import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.entity.QuizSetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface QuizSetRepository extends JpaRepository<QuizSet, UUID> {
    Optional<QuizSet> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant from);
    long countByUserIdAndDocumentIdAndDeletedAtIsNull(UUID userId, UUID documentId);
    Optional<QuizSet> findFirstByCacheKeyAndStatusAndDeletedAtIsNullAndIdNotOrderByCreatedAtAsc(
            String cacheKey, QuizSetStatus status, UUID excludeId);
    Page<QuizSet> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<QuizSet> findByUserIdAndDocumentIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID userId, UUID documentId, Pageable pageable);
}
