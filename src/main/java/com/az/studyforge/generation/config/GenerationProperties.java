package com.az.studyforge.generation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.generation")
public record GenerationProperties(
        @DefaultValue("Asia/Ho_Chi_Minh") ZoneId quotaZone,
        @DefaultValue("2") int maxConcurrentJobs,
        @DefaultValue("3") int llmConcurrency,
        @DefaultValue("2") int chunkMaxAttempts,
        @DefaultValue("15m") Duration stuckTimeout,
        @DefaultValue("2") int topUpRounds) {}
