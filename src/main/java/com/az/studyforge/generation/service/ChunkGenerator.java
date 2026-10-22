package com.az.studyforge.generation.service;

import com.az.studyforge.generation.llm.LlmClient;
import com.az.studyforge.generation.llm.dto.LlmRequest;
import com.az.studyforge.generation.llm.dto.LlmResult;
import com.az.studyforge.generation.llm.model.ChunkSpec;
import com.az.studyforge.generation.llm.model.ParseResult;
import com.az.studyforge.generation.llm.parse.InvalidLlmOutputException;
import com.az.studyforge.generation.llm.parse.QuestionParser;
import com.az.studyforge.generation.llm.promt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkGenerator {

    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final QuestionParser parser;

    public ParseResult generate(ChunkSpec spec, UUID quizSetId, Long chunkId) {
        int maxTokens = promptBuilder.maxTokens(spec);
        LlmResult res = llmClient.complete(new LlmRequest(
                promptBuilder.systemPrompt(), promptBuilder.userPrompt(spec), maxTokens, quizSetId, chunkId));
        try {
            return parser.parse(res.content(), spec);
        } catch (InvalidLlmOutputException e) {
            String c = res.content();
            log.warn("Unparseable output for quiz set {} chunk {}: {} | {} chars, {}/{} tokens | start: {} | end: {}",
                    quizSetId, chunkId, e.getMessage(), c.length(), res.completionTokens(), maxTokens,
                    c.substring(0, Math.min(300, c.length())).replace('\n', ' '),
                    c.substring(Math.max(0, c.length() - 300)).replace('\n', ' '));
            throw e;
        }
    }
}
