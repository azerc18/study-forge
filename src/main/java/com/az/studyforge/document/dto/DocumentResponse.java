package com.az.studyforge.document.dto;

import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String originalFilename,
        FileType fileType,
        long fileSizeBytes,
        DocumentStatus status,
        Integer pageCount,
        Integer wordCount,
        String errorMessage,
        Instant createdAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getFileType(), d.getFileSizeBytes(),
                d.getStatus(), d.getPageCount(), d.getWordCount(), d.getErrorMessage(), d.getCreatedAt());
    }
}
