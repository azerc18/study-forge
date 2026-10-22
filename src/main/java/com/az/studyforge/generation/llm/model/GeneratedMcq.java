package com.az.studyforge.generation.llm.model;

import java.util.List;

public record GeneratedMcq(String content, List<String> options, int correctIndex,
                           String explanation, List<Integer> sourcePages,
                           String normalizedHash) implements GeneratedQuestion {}
