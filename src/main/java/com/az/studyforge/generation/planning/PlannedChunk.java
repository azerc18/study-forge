package com.az.studyforge.generation.planning;

import com.az.studyforge.generation.llm.model.PageText;

import java.util.List;

public record PlannedChunk(int index, List<PageText> pages, int wordCount, int mcqTarget, int essayTarget) {

    public PlannedChunk {
        pages = List.copyOf(pages);
    }

    public int pageFrom() { return pages.getFirst().pageNumber(); }
    public int pageTo()   { return pages.getLast().pageNumber(); }
}
