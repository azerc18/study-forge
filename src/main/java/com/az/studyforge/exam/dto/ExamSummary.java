package com.az.studyforge.exam.dto;

import com.az.studyforge.exam.entity.ExamAttempt;
import com.az.studyforge.exam.entity.ExamStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** 1 dòng trong lịch sử thi. */
public record ExamSummary(
        UUID id,
        ExamStatus status,
        int total,
        Integer correctCount,
        BigDecimal score,
        Integer timeLimitSeconds,
        Instant startedAt,
        Instant submittedAt,
        Long durationSeconds) {

    public static ExamSummary from(ExamAttempt a) {
        return new ExamSummary(a.getId(), a.getStatus(), a.getTotal(), a.getCorrectCount(), a.getScore(),
                a.getTimeLimitSeconds(), a.getStartedAt(), a.getSubmittedAt(), durationSeconds(a));
    }

    public static Long durationSeconds(ExamAttempt a) {
        return a.getSubmittedAt() == null ? null : Duration.between(a.getStartedAt(), a.getSubmittedAt()).toSeconds();
    }
}
