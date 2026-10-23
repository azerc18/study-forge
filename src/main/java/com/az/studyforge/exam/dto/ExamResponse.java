package com.az.studyforge.exam.dto;

import com.az.studyforge.exam.entity.ExamStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExamResponse(
        UUID id,
        UUID quizSetId,
        String quizSetTitle,
        ExamStatus status,
        int total,
        Integer timeLimitSeconds,
        Instant startedAt,
        Instant deadline,               // null = không giới hạn
        Instant submittedAt,
        Integer correctCount,
        BigDecimal score,
        Long durationSeconds,
        List<ExamQuestionView> questions,
        List<TopicResult> topics) {}    // rỗng khi đang làm; sau khi nộp: chủ đề yếu nhất trước
