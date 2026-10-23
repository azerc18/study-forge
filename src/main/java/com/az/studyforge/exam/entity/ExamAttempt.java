package com.az.studyforge.exam.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "quiz_set_id", nullable = false, updatable = false)
    private UUID quizSetId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExamStatus status = ExamStatus.IN_PROGRESS;

    @Column(name = "time_limit_seconds")
    private Integer timeLimitSeconds;

    @Column(nullable = false)
    private int total;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(precision = 3, scale = 1)
    private BigDecimal score;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    public Instant deadline() {
        return timeLimitSeconds == null ? null : startedAt.plusSeconds(timeLimitSeconds);
    }
}