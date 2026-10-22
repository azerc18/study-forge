package com.az.studyforge.document.service;

import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.document.dto.DocumentForGeneration;
import com.az.studyforge.document.dto.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DocumentService {
    DocumentResponse upload(UUID userId, MultipartFile file);
    PageResponse<DocumentResponse> list(UUID userId, int page, int size);
    DocumentResponse get(UUID userId, UUID documentId);
    void delete(UUID userId, UUID documentId);
    DocumentForGeneration getForGeneration(UUID userId, UUID documentId);
}
