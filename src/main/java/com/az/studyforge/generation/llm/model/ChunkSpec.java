package com.az.studyforge.generation.llm.model;

import com.az.studyforge.generation.domain.OutputLanguage;

import java.util.List;

public record ChunkSpec(
        int pageFrom,
        int pageTo,
        List<PageText> pages,
        int mcqTarget,
        int essayTarget,
        OutputLanguage language,
        List<String> avoidQuestions) {

    public ChunkSpec {
        pages = List.copyOf(pages);
        avoidQuestions = avoidQuestions == null ? List.of() : List.copyOf(avoidQuestions);
    }
}
