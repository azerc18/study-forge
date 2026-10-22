package com.az.studyforge.generation.llm.model;

import java.util.List;

public record GeneratedEssay(String content, String modelAnswer, List<String> rubric,
                             List<Integer> sourcePages,
                             String normalizedHash) implements GeneratedQuestion {}
