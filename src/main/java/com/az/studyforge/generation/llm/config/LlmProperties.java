package com.az.studyforge.generation.llm.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        @DefaultValue("2") @Min(0) @Max(5) int maxRetries,
        @DefaultValue @Valid List<Provider> providers) {

    public record Provider(
            @NotBlank String name,
            @NotBlank String baseUrl,
            String apiKey,
            @NotBlank String model,
            @DefaultValue("90s") Duration timeout,
            @DefaultValue("8000") @Positive int maxTokens,
            @DefaultValue("0.7") double temperature,
            Map<String, Object> extraBody) {

        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "Provider[name=%s, model=%s, enabled=%s]".formatted(name, model, enabled());
        }
    }
}
