package com.az.studyforge.generation.llm.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
        @DefaultValue @Valid List<Provider> providers,
        @DefaultValue("0.5") @PositiveOrZero double dailyBudgetUsd) {

    public record Provider(
            @NotBlank String name,
            @NotBlank String baseUrl,
            String apiKey,
            @NotBlank String model,
            @DefaultValue("90s") Duration timeout,
            @DefaultValue("8000") @Positive int maxTokens,
            @DefaultValue("0.7") double temperature,
            Map<String, Object> extraBody,
            @DefaultValue("0") @PositiveOrZero double inputPricePerMillion,
            @DefaultValue("0") @PositiveOrZero double outputPricePerMillion) {

        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }

        public double costUsd(long promptTokens, long completionTokens){
            return (inputPricePerMillion*promptTokens + outputPricePerMillion*completionTokens)/1000000L;
        }

        @Override
        public String toString() {
            return "Provider[name=%s, model=%s, enabled=%s]".formatted(name, model, enabled());
        }
    }
}
