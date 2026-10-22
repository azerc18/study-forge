package com.az.studyforge.generation.repository;

import com.az.studyforge.generation.entity.GenerationChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GenerationChunkRepository extends JpaRepository<GenerationChunk, Long> {
    List<GenerationChunk> findByQuizSetIdOrderByChunkIndex(UUID quizSetId);
}
