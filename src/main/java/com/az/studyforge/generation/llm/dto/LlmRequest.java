package com.az.studyforge.generation.llm.dto;

import com.az.studyforge.generation.llm.entity.LlmPurpose;

import java.util.UUID;

public record LlmRequest(String systemPrompt, String userPrompt, Integer maxTokens,
                         UUID quizSetId, Long chunkId, LlmPurpose purpose) {

    public LlmRequest(String systemPrompt, String userPrompt, Integer maxTokens, UUID quizSetId, Long chunkId) {
        this(systemPrompt, userPrompt, maxTokens, quizSetId, chunkId, LlmPurpose.GENERATION);
    }
}
