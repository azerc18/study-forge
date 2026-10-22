package com.az.studyforge.feedback.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EssayFeedbackResponse(
        UUID id, UUID questionId, String answer, BigDecimal score,
        List<RubricResult> rubric, String summary, List<String> corrections,
        List<String> suggestions, String improvedAnswer, Instant createdAt) {}
