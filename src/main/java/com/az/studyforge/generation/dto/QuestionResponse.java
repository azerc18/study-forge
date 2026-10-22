package com.az.studyforge.generation.dto;

import com.az.studyforge.generation.domain.QuestionType;

import java.util.List;
import java.util.UUID;

public record QuestionResponse(
        UUID id, int orderIndex, QuestionType type, String content, List<OptionResponse> options,
        String explanation, String modelAnswer, List<String> rubric, List<Integer> sourcePages,
        String topic) {}
