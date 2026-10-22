package com.az.studyforge.generation.llm.model;

import java.util.List;

public record GeneratedEssay(String content, String modelAnswer, List<String> rubric,
                             List<Integer> sourcePages, String normalizedHash,
                             String topic) implements GeneratedQuestion {

    public GeneratedEssay(String content, String modelAnswer, List<String> rubric,
                          List<Integer> sourcePages, String normalizedHash) {
        this(content, modelAnswer, rubric, sourcePages, normalizedHash, null);
    }
}
