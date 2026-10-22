package com.az.studyforge.generation.repository;

import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
}
