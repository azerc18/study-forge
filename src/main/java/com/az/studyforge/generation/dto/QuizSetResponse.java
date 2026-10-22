package com.az.studyforge.generation.dto;

import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.entity.QuizSetStatus;

import java.time.Instant;
import java.util.UUID;

public record QuizSetResponse(
        UUID id,
        UUID documentId,
        String title,
        QuestionType questionType,
        int requestedCount,
        OutputLanguage language,
        QuizSetStatus status,
        int generatedCount,
        int totalChunks,
        int completedChunks,
        String errorMessage,
        Instant createdAt) {

    public static QuizSetResponse from(QuizSet s) {
        return new QuizSetResponse(s.getId(), s.getDocumentId(), s.getTitle(), s.getQuestionType(),
                s.getRequestedCount(), s.getLanguage(), s.getStatus(), s.getGeneratedCount(),
                s.getTotalChunks(), s.getCompletedChunks(), s.getErrorMessage(), s.getCreatedAt());
    }
}
