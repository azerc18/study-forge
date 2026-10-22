package com.az.studyforge.generation.llm;

import com.az.studyforge.generation.llm.dto.LlmRequest;
import com.az.studyforge.generation.llm.dto.LlmResult;
import com.az.studyforge.generation.llm.entity.LlmCall;
import com.az.studyforge.generation.llm.exception.LlmException;
import com.az.studyforge.generation.llm.repository.LlmCallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmCallLogger {

    private final LlmCallRepository repository;

    public void success(LlmRequest req, LlmResult res, int latencyMs) {
        save(LlmCall.builder()
                .quizSetId(req.quizSetId()).chunkId(req.chunkId())
                .provider(res.provider()).model(res.model())
                .promptTokens(res.promptTokens()).completionTokens(res.completionTokens())
                .latencyMs(latencyMs).success(true).httpStatus(200)
                .build());
    }

    public void failure(LlmRequest req, String provider, String model, LlmException e, int latencyMs) {
        String msg = e.getMessage() == null ? null
                : e.getMessage().length() <= 500 ? e.getMessage() : e.getMessage().substring(0, 500);
        save(LlmCall.builder()
                .quizSetId(req.quizSetId()).chunkId(req.chunkId())
                .provider(provider).model(model)
                .latencyMs(latencyMs).success(false).httpStatus(e.getHttpStatus())
                .errorMessage(msg)
                .build());
    }

    private void save(LlmCall call) {
        try {
            repository.save(call);
        } catch (RuntimeException ex) {
            log.warn("Could not save llm_calls row", ex);
        }
    }
}
