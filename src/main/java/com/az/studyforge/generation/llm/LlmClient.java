package com.az.studyforge.generation.llm;

import com.az.studyforge.generation.llm.dto.LlmRequest;
import com.az.studyforge.generation.llm.dto.LlmResult;

public interface LlmClient {
    LlmResult complete(LlmRequest request);
}
