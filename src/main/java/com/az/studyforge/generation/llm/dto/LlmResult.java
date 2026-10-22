package com.az.studyforge.generation.llm.dto;

public record LlmResult(String content, String provider, String model,
                        Integer promptTokens, Integer completionTokens) {}
