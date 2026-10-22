package com.az.studyforge.document.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.document")
public record DocumentProperties(
        @Positive int maxPages,
        @Positive int shortDocumentWords
) {}
