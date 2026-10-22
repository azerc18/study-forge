package com.az.studyforge.generation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.generation")
public record GenerationProperties(
        @DefaultValue("Asia/Ho_Chi_Minh") ZoneId quotaZone) {}
