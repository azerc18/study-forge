package com.az.studyforge.document.dto;

import java.util.UUID;

public record DocumentForGeneration(UUID id, String originalFilename, int wordCount, String fileHash) {}
