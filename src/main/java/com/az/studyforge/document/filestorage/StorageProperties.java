package com.az.studyforge.document.filestorage;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(@NotNull Path localDir) {}
