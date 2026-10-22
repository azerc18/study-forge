package com.az.studyforge.generation.entity;

import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_sets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 10)
    private QuestionType questionType;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private OutputLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private QuizSetStatus status = QuizSetStatus.QUEUED;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "completed_chunks", nullable = false)
    private int completedChunks;

    @Column(name = "cache_key", nullable = false, length = 64)
    private String cacheKey;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "use_cache", nullable = false)
    @Builder.Default
    private boolean useCache = true;
}
