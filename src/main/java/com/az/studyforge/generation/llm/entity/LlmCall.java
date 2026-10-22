package com.az.studyforge.generation.llm.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_calls")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmCall {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_set_id")
    private UUID quizSetId;

    @Column(name = "chunk_id")
    private Long chunkId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
