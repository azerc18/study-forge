package com.az.studyforge.generation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "question_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, length = 1)
    private String label;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;      // đặt tên "correct" để Lombok sinh isCorrect(), không phải isIsCorrect()
}
