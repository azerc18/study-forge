package com.az.studyforge.feedback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.feedback")
public record FeedbackProperties(
        @DefaultValue("30") int dailyLimit,
        @DefaultValue("3000") int materialMaxWords) {}
