package com.az.studyforge.generation.entity;

import com.az.studyforge.generation.domain.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "quiz_set_id", nullable = false, updatable = false)
    private UUID quizSetId;

    @Column(name = "chunk_id")
    private Long chunkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuestionType type;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String explanation;

    @Column(name = "model_answer", columnDefinition = "text")
    private String modelAnswer;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String rubric;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_pages", nullable = false)
    private List<Integer> sourcePages;

    @Column(name = "normalized_hash", nullable = false, length = 64)
    private String normalizedHash;

    @Column(length = 80)
    private String topic;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("label")
    @Builder.Default
    private List<QuestionOption> options = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addOption(QuestionOption option) {
        option.setQuestion(this);
        options.add(option);
    }
}