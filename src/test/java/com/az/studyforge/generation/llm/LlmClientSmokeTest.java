package com.az.studyforge.generation.llm;

import com.az.studyforge.generation.llm.dto.LlmRequest;
import com.az.studyforge.generation.llm.dto.LlmResult;
import com.az.studyforge.generation.llm.entity.LlmCall;
import com.az.studyforge.generation.llm.repository.LlmCallRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.az.studyforge.TestcontainersConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LlmClientSmokeTest {

    @Autowired
    LlmClient llmClient;
    @Autowired
    LlmCallRepository llmCallRepository;

    @Test
    void deepseek_returnsJsonAndLogsCall() {
        LlmResult r = llmClient.complete(new LlmRequest(
                "Answer in json like {\"answer\": \"...\"}", "What is 2 + 2?", 100, null, null));

        assertThat(r.provider()).isEqualTo("deepseek");
        assertThat(r.content()).contains("\"answer\"");
        assertThat(r.promptTokens()).isPositive();
        assertThat(llmCallRepository.findAll()).anyMatch(LlmCall::isSuccess);
    }
}
