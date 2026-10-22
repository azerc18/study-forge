package com.az.studyforge.document.controller;

import com.az.studyforge.auth.config.SecurityConfig;
import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.document.dto.DocumentResponse;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.service.DocumentService;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class DocumentControllerTest {

    private static final String BASE = "/api/documents";

    @Autowired MockMvc mockMvc;

    @MockitoBean DocumentService documentService;
    @MockitoBean JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();

    private RequestPostProcessor authenticated() {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("role", "USER"));
    }

    private static DocumentResponse response(UUID id, String name, DocumentStatus status) {
        return new DocumentResponse(id, name, FileType.PDF, 1234L, status,
                3, 820, null, Instant.parse("2026-10-20T10:00:00Z"));
    }

    // ===== GET /api/documents =====

    // C1
    @Test
    void list_returnsPageResponse() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.list(userId, 1, 5)).thenReturn(new PageResponse<>(
                List.of(response(id, "lecture.pdf", DocumentStatus.READY)), 1, 5, 6, 2, false));

        mockMvc.perform(get(BASE).param("page", "1").param("size", "5").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.result.content.length()").value(1))
                .andExpect(jsonPath("$.result.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.result.content[0].originalFilename").value("lecture.pdf"))
                .andExpect(jsonPath("$.result.content[0].status").value("READY"))
                .andExpect(jsonPath("$.result.page").value(1))
                .andExpect(jsonPath("$.result.size").value(5))
                .andExpect(jsonPath("$.result.totalElements").value(6))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    // C2
    @Test
    void list_withoutParams_usesDefaults() throws Exception {
        when(documentService.list(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get(BASE).with(authenticated()))
                .andExpect(status().isOk());

        verify(documentService).list(userId, 0, 20);
    }

    // ===== GET /api/documents/{id} =====

    // C3: response không được lộ storageKey / fileHash
    @Test
    void get_ownDocument_returns200WithoutInternalFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.get(userId, id)).thenReturn(response(id, "slides.pdf", DocumentStatus.EXTRACTING));

        mockMvc.perform(get(BASE + "/" + id).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(id.toString()))
                .andExpect(jsonPath("$.result.fileType").value("PDF"))
                .andExpect(jsonPath("$.result.status").value("EXTRACTING"))
                .andExpect(jsonPath("$.result.createdAt").exists())
                .andExpect(jsonPath("$.result.storageKey").doesNotExist())
                .andExpect(jsonPath("$.result.fileHash").doesNotExist())
                .andExpect(jsonPath("$.result.userId").doesNotExist());
    }

    // C4
    @Test
    void get_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.get(userId, id)).thenThrow(new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        mockMvc.perform(get(BASE + "/" + id).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENT_NOT_FOUND"));
    }

    // C5: "abc" không phải UUID → 400, không được rơi vào catch-all 500
    @Test
    void get_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/abc").with(authenticated()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.id").value("Invalid value"));

        verifyNoInteractions(documentService);
    }

    // ===== DELETE /api/documents/{id} =====

    // C6
    @Test
    void delete_ownDocument_returns204WithEmptyBody() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/" + id).with(authenticated()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(documentService).delete(userId, id);
    }

    // C7
    @Test
    void delete_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AppException(ErrorCode.DOCUMENT_NOT_FOUND)).when(documentService).delete(userId, id);

        mockMvc.perform(delete(BASE + "/" + id).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENT_NOT_FOUND"));
    }

    // ===== Không có token =====

    static Stream<Named<MockHttpServletRequestBuilder>> protectedRequests() {
        String id = UUID.randomUUID().toString();
        return Stream.of(
                Named.of("GET list", get(BASE)),
                Named.of("GET one", get(BASE + "/" + id)),
                Named.of("DELETE", delete(BASE + "/" + id)));
    }

    // C8
    @ParameterizedTest(name = "{0} without token -> 401")
    @MethodSource("protectedRequests")
    void noToken_returns401(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(documentService);
    }
}