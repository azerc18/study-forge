package com.az.studyforge.generation.service;

import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.generation.dto.QuestionResponse;
import com.az.studyforge.generation.dto.QuizSetResponse;

import java.util.List;
import java.util.UUID;

public interface QuizSetManagementService {
    PageResponse<QuizSetResponse> list(UUID userId, UUID documentId, int page, int size);
    List<QuestionResponse> getQuestions(UUID userId, UUID quizSetId);
    QuizSetResponse rename(UUID userId, UUID quizSetId, String title);
    void delete(UUID userId, UUID quizSetId);
    QuizSetResponse retry(UUID userId, UUID quizSetId);
}
