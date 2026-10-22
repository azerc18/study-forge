package com.az.studyforge.generation.llm.model;

import java.util.List;

public record ParseResult(List<GeneratedQuestion> questions, List<String> rejected) {

    public long mcqCount()   { return questions.stream().filter(GeneratedMcq.class::isInstance).count(); }
    public long essayCount() { return questions.stream().filter(GeneratedEssay.class::isInstance).count(); }
}
