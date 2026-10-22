package com.az.studyforge.generation.service;

import com.az.studyforge.generation.dto.CreateQuizSetRequest;
import com.az.studyforge.generation.dto.QuizSetResponse;
import com.az.studyforge.generation.dto.QuotaResponse;

import java.util.UUID;

public interface QuizSetService {
    QuizSetResponse create(UUID userId, CreateQuizSetRequest request);
    QuizSetResponse get(UUID userId, UUID quizSetId);
    QuotaResponse quota(UUID userId);
}
