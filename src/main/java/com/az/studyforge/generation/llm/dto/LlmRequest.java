package com.az.studyforge.generation.llm.dto;

import java.util.UUID;

public record LlmRequest(String systemPrompt, String userPrompt, Integer maxTokens,
                         UUID quizSetId, Long chunkId) {}
