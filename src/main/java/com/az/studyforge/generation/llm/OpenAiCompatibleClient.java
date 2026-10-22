package com.az.studyforge.generation.llm;

import com.az.studyforge.generation.llm.config.LlmProperties;
import com.az.studyforge.generation.llm.dto.LlmRequest;
import com.az.studyforge.generation.llm.dto.LlmResult;
import com.az.studyforge.generation.llm.exception.LlmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
class OpenAiCompatibleClient {

    private record RawResponse(int status, String retryAfter, String body) {
    }

    private final LlmProperties.Provider provider;
    private final RestClient rest;
    private final JsonMapper json;

    OpenAiCompatibleClient(LlmProperties.Provider provider, JsonMapper json) {
        this.provider = provider;
        this.json = json;

        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(provider.timeout());

        this.rest = RestClient.builder()
                .baseUrl(provider.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey())
                .build();
    }

    String name() {
        return provider.name();
    }

    String model() {
        return provider.model();
    }

    LlmResult complete(LlmRequest request) {
        String body = json.writeValueAsString(requestBody(request));

        RawResponse res;
        try {
            res = rest.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((req, response) -> new RawResponse(
                            response.getStatusCode().value(),
                            response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (ResourceAccessException e) {
            throw new LlmException("Network error: " + e.getMessage(), true, null, null, e);
        }

        if (res.status() != 200) throw httpError(res);
        return parse(res.body());
    }

    private Map<String, Object> requestBody(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", provider.temperature());
        body.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : provider.maxTokens());
        if (provider.extraBody() != null) body.putAll(provider.extraBody());
        return body;
    }

    private LlmResult parse(String raw) {
        JsonNode root = json.readTree(raw);
        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("message").path("content").asString();

        if ("length".equals(choice.path("finish_reason").asString())) {
            throw new LlmException("Response truncated by max_tokens", false, 200);
        }
        if (content == null || content.isBlank()) {
            throw new LlmException("Empty content", true, 200);
        }

        JsonNode usage = root.path("usage");
        return new LlmResult(content, provider.name(), provider.model(),
                usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null,
                usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null);
    }

    private LlmException httpError(RawResponse res) {
        int status = res.status();
        boolean retryable = status == 429 || status >= 500;   // 400/401/402/403: retry cũng vô ích
        String snippet = res.body().length() > 300 ? res.body().substring(0, 300) : res.body();
        return new LlmException("HTTP " + status + ": " + snippet, retryable, status,
                parseRetryAfter(res.retryAfter()), null);
    }

    private static Duration parseRetryAfter(String header) {
        if (header == null) return null;
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
