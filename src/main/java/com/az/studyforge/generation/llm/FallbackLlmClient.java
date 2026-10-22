package com.az.studyforge.generation.llm;

import com.az.studyforge.generation.llm.config.LlmProperties;
import com.az.studyforge.generation.llm.dto.LlmRequest;
import com.az.studyforge.generation.llm.dto.LlmResult;
import com.az.studyforge.generation.llm.exception.LlmException;
import com.az.studyforge.generation.llm.exception.LlmUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class FallbackLlmClient implements LlmClient {

    private static final Duration MAX_WAIT = Duration.ofSeconds(30);

    private final List<OpenAiCompatibleClient> clients;
    private final int maxRetries;
    private final LlmCallLogger callLogger;

    public FallbackLlmClient(LlmProperties props, JsonMapper jsonMapper, LlmCallLogger callLogger) {
        this.clients = props.providers().stream()
                .filter(LlmProperties.Provider::enabled)
                .map(p -> new OpenAiCompatibleClient(p, jsonMapper))
                .toList();
        this.maxRetries = props.maxRetries();
        this.callLogger = callLogger;
        log.info("LLM providers in order: {}", clients.stream().map(OpenAiCompatibleClient::name).toList());
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        if (clients.isEmpty()) throw new LlmUnavailableException("No LLM provider configured");

        LlmException last = null;
        for (OpenAiCompatibleClient client : clients) {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                long start = System.nanoTime();
                try {
                    LlmResult result = client.complete(request);
                    callLogger.success(request, result, elapsedMs(start));
                    return result;
                } catch (LlmException e) {
                    callLogger.failure(request, client.name(), client.model(), e, elapsedMs(start));
                    last = e;
                    log.warn("LLM {} attempt {} failed: {}", client.name(), attempt + 1, e.getMessage());
                    if (!e.isRetryable() || attempt == maxRetries) break;   // sang provider kế tiếp
                    sleep(backoff(attempt, e.getRetryAfter()));
                }
            }
        }
        throw new LlmUnavailableException("All LLM providers failed", last);
    }

    private static Duration backoff(int attempt, Duration retryAfter) {
        if (retryAfter != null) return retryAfter.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : retryAfter;
        long ms = 1000L * (1L << attempt) + ThreadLocalRandom.current().nextLong(500);
        return Duration.ofMillis(ms);
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("Interrupted while waiting to retry", e);
        }
    }

    private static int elapsedMs(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }
}
