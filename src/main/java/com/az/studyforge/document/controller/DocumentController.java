package com.az.studyforge.document.controller;

import com.az.studyforge.common.dto.ApiResponse;
import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.security.CurrentUser;
import com.az.studyforge.document.dto.DocumentResponse;
import com.az.studyforge.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file) {

        DocumentResponse doc = documentService.upload(CurrentUser.id(jwt), file);

        return ResponseEntity.accepted()
                .location(URI.create("/api/documents/" + doc.id()))
                .body(ApiResponse.success(HttpStatus.ACCEPTED, doc, "Document uploaded, extraction in progress"));
    }

    @GetMapping
    public ApiResponse<PageResponse<DocumentResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(documentService.list(CurrentUser.id(jwt), page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.success(documentService.get(CurrentUser.id(jwt), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        documentService.delete(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
