package com.az.studyforge.generation.llm.model;

import java.util.List;

public record GeneratedMcq(String content, List<String> options, int correctIndex,
                           String explanation, List<Integer> sourcePages,
                           String normalizedHash, String topic) implements GeneratedQuestion {

    public GeneratedMcq(String content, List<String> options, int correctIndex,
                        String explanation, List<Integer> sourcePages, String normalizedHash) {
        this(content, options, correctIndex, explanation, sourcePages, normalizedHash, null);
    }
}
