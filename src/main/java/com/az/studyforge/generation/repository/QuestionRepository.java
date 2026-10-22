package com.az.studyforge.generation.repository;

import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    @Query("select q.normalizedHash from Question q where q.quizSetId = :quizSetId")
    Set<String> findHashesByQuizSetId(@Param("quizSetId") UUID quizSetId);

    long countByQuizSetId(UUID quizSetId);

    long countByChunkId(Long chunkId);

    @Modifying
    @Query(value = """
            UPDATE questions q SET order_index = r.rn
            FROM (SELECT id, row_number() OVER (ORDER BY order_index, created_at, id) AS rn
                  FROM questions WHERE quiz_set_id = :quizSetId) r
            WHERE q.id = r.id
            """, nativeQuery = true)
    int renumber(@Param("quizSetId") UUID quizSetId);

    long countByQuizSetIdAndType(UUID quizSetId, QuestionType type);

    @Query("select q.content from Question q where q.chunkId = :chunkId order by q.orderIndex")
    List<String> findContentsByChunkId(@Param("chunkId") Long chunkId);

    @Query("select distinct q from Question q left join fetch q.options where q.quizSetId = :quizSetId order by q.orderIndex")
    List<Question> findWithOptionsByQuizSetId(@Param("quizSetId") UUID quizSetId);

    @Query("""
        select q from Question q
        where q.id = :id
          and exists (select 1 from QuizSet s
                      where s.id = q.quizSetId and s.userId = :userId and s.deletedAt is null)
        """)
    Optional<Question> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * E4: câu hỏi trong các bộ cũ của user trên cùng tài liệu, có trích trang nằm trong [pageFrom, pageTo].
     * Mới nhất trước. Dùng làm danh sách "đừng lặp lại" khi tạo bộ khác.
     */
    @Query(value = """
            SELECT q.content FROM questions q
            JOIN quiz_sets s ON s.id = q.quiz_set_id
            WHERE s.user_id = :userId
              AND s.document_id = :documentId
              AND s.id <> :excludeId
              AND s.deleted_at IS NULL
              AND q.type IN (:types)
              AND EXISTS (SELECT 1 FROM unnest(q.source_pages) p WHERE p BETWEEN :pageFrom AND :pageTo)
            ORDER BY s.created_at DESC, q.order_index
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findPreviousContents(@Param("userId") UUID userId,
                                      @Param("documentId") UUID documentId,
                                      @Param("excludeId") UUID excludeId,
                                      @Param("pageFrom") int pageFrom,
                                      @Param("pageTo") int pageTo,
                                      @Param("types") List<String> types,
                                      @Param("limit") int limit);
}
