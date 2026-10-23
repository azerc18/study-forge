package com.az.studyforge.generation.repository;

import com.az.studyforge.generation.dto.UserQuizSetCount;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.entity.QuizSetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    /** Admin: đếm bộ đề cho cả 1 trang user bằng 1 query (tránh N+1). Đếm cả bộ đã xoá, giống quota. */
    @Query("""
            select new com.az.studyforge.generation.dto.UserQuizSetCount(
                       s.userId, count(s), sum(case when s.createdAt >= :since then 1L else 0L end))
            from QuizSet s
            where s.userId in :userIds
            group by s.userId
            """)
    List<UserQuizSetCount> countByUsers(@Param("userIds") Collection<UUID> userIds, @Param("since") Instant since);
}
