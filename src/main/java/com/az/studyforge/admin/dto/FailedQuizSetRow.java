package com.az.studyforge.admin.dto;

import com.az.studyforge.generation.domain.QuestionType;

import java.time.Instant;
import java.util.UUID;

/** errorMessage: câu báo chung cho user. lastError: lỗi kỹ thuật thật từ generation_jobs.last_error. */
public record FailedQuizSetRow(
        UUID quizSetId,
        String title,
        String userEmail,
        QuestionType questionType,
        int requestedCount,
        String errorMessage,
        String lastError,
        Instant createdAt) {}
