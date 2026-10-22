package com.az.studyforge.generation.dto;

import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateQuizSetRequest(
        @NotNull UUID documentId,
        @NotNull QuestionType questionType,
        @NotNull @Min(1) @Max(50) Integer count,
        @NotNull OutputLanguage language,
        @Size(max = 200) String title) {}
