package com.az.studyforge.generation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "generation_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerationChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_set_id", nullable = false, updatable = false)
    private UUID quizSetId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "page_from", nullable = false)
    private int pageFrom;

    @Column(name = "page_to", nullable = false)
    private int pageTo;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "mcq_target", nullable = false)
    private int mcqTarget;

    @Column(name = "essay_target", nullable = false)
    private int essayTarget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChunkStatus status = ChunkStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
