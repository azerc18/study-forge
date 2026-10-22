package com.az.studyforge.generation.dto;

import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GenerationOptionsResponse(
        UUID documentId,
        int wordCount,
        boolean shortDocument,
        Map<QuestionType, List<Integer>> questionCounts,
        List<OutputLanguage> languages) {}
