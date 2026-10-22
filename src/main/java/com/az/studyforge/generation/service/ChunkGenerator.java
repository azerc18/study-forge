package com.az.studyforge.generation.service;

import com.az.studyforge.generation.llm.LlmClient;
import com.az.studyforge.generation.llm.dto.LlmRequest;
import com.az.studyforge.generation.llm.dto.LlmResult;
import com.az.studyforge.generation.llm.model.ChunkSpec;
import com.az.studyforge.generation.llm.model.ParseResult;
import com.az.studyforge.generation.llm.parse.QuestionParser;
import com.az.studyforge.generation.llm.promt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChunkGenerator {

    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final QuestionParser parser;

    public ParseResult generate(ChunkSpec spec, UUID quizSetId, Long chunkId) {
        LlmResult res = llmClient.complete(new LlmRequest(
                promptBuilder.systemPrompt(), promptBuilder.userPrompt(spec),
                promptBuilder.maxTokens(spec), quizSetId, chunkId));
        return parser.parse(res.content(), spec);
    }
}
