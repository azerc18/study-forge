package com.az.studyforge.generation.llm.model;

import java.util.List;

public sealed interface GeneratedQuestion permits GeneratedMcq, GeneratedEssay {
    String content();
    List<Integer> sourcePages();
    String normalizedHash();
}