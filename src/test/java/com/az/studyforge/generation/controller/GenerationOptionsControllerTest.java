package com.az.studyforge.generation.controller;

import com.az.studyforge.auth.config.SecurityConfig;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.document.config.DocumentProperties;
import com.az.studyforge.document.dto.DocumentResponse;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.service.DocumentService;
import com.az.studyforge.generation.policy.QuestionLimitPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Dùng QuestionLimitPolicy THẬT (không mock) để test luôn đường đi controller → policy → JSON.
 * Chỉ mock DocumentService.
 */
@WebMvcTest(GenerationOptionsController.class)
@Import({SecurityConfig.class, QuestionLimitPolicy.class, GenerationOptionsControllerTest.LimitsConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.document.max-pages=100",
        "app.document.short-document-words=1500"
})
class GenerationOptionsControllerTest {

    /** @WebMvcTest không quét @ConfigurationProperties, nên phải bật DocumentProperties bằng tay. */
    @TestConfiguration
    @EnableConfigurationProperties(DocumentProperties.class)
    static class LimitsConfig {}

    @Autowired MockMvc mockMvc;

    @MockitoBean DocumentService documentService;
    @MockitoBean JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();
    private final UUID docId = UUID.randomUUID();

    private String url() {
        return "/api/documents/" + docId + "/options";
    }

    private RequestPostProcessor authenticated() {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("role", "USER"));
    }

    private DocumentResponse doc(DocumentStatus status, Integer wordCount) {
        return new DocumentResponse(docId, "lecture.pdf", FileType.PDF, 1234L, status,
                wordCount == null ? null : 3, wordCount, null, Instant.parse("2026-10-20T10:00:00Z"));
    }

    // O1
    @Test
    void readyShortDocument_returnsSmallSetsOnly() throws Exception {
        when(documentService.get(userId, docId)).thenReturn(doc(DocumentStatus.READY, 820));

        mockMvc.perform(get(url()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.documentId").value(docId.toString()))
                .andExpect(jsonPath("$.result.wordCount").value(820))
                .andExpect(jsonPath("$.result.shortDocument").value(true))
                .andExpect(jsonPath("$.result.questionCounts.MCQ", contains(10)))
                .andExpect(jsonPath("$.result.questionCounts.ESSAY", contains(3)))
                .andExpect(jsonPath("$.result.questionCounts.MIXED", contains(10)))
                .andExpect(jsonPath("$.result.languages", contains("EN", "VI")));
    }

    // O2
    @Test
    void readyNormalDocument_returnsAllSizes() throws Exception {
        when(documentService.get(userId, docId)).thenReturn(doc(DocumentStatus.READY, 3000));

        mockMvc.perform(get(url()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.shortDocument").value(false))
                .andExpect(jsonPath("$.result.questionCounts.MCQ", contains(10, 30, 50)))
                .andExpect(jsonPath("$.result.questionCounts.ESSAY", contains(3, 5, 7)))
                .andExpect(jsonPath("$.result.questionCounts.MIXED", contains(10, 30, 50)));
    }

    // O3: đúng 1500 từ thì không còn là tài liệu ngắn
    @Test
    void exactlyAtThreshold_isNotShort() throws Exception {
        when(documentService.get(userId, docId)).thenReturn(doc(DocumentStatus.READY, 1500));

        mockMvc.perform(get(url()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.shortDocument").value(false));
    }

    // O4: frontend render các nút theo đúng thứ tự key trong JSON
    @Test
    void questionCounts_keepEnumOrderInJson() throws Exception {
        when(documentService.get(userId, docId)).thenReturn(doc(DocumentStatus.READY, 3000));

        String body = mockMvc.perform(get(url()).with(authenticated()))
                .andReturn().getResponse().getContentAsString();

        int mcq = body.indexOf("\"MCQ\"");
        int essay = body.indexOf("\"ESSAY\"");
        int mixed = body.indexOf("\"MIXED\"");
        assertThat(mcq).isPositive().isLessThan(essay);
        assertThat(essay).isLessThan(mixed);
    }

    // O5: chưa READY thì wordCount có thể null → phải trả 409, không được NPE ra 500
    @ParameterizedTest(name = "{0} -> 409")
    @EnumSource(value = DocumentStatus.class, names = {"UPLOADED", "EXTRACTING", "FAILED"})
    void notReady_returns409(DocumentStatus status) throws Exception {
        when(documentService.get(userId, docId)).thenReturn(doc(status, null));

        mockMvc.perform(get(url()).with(authenticated()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENT_NOT_READY"));
    }

    // O6
    @Test
    void notOwnedOrMissing_returns404() throws Exception {
        when(documentService.get(userId, docId)).thenThrow(new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        mockMvc.perform(get(url()).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENT_NOT_FOUND"));
    }

    // O7
    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get(url()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(documentService);
    }
}