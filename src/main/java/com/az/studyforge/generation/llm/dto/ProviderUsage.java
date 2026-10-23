package com.az.studyforge.generation.llm.dto;

public record ProviderUsage(
        String provider,
        long promptTokens,
        long completionTokens
) {
}
