package com.az.studyforge.generation.controller;

import com.az.studyforge.auth.config.SecurityConfig;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.dto.CreateQuizSetRequest;
import com.az.studyforge.generation.dto.QuizSetResponse;
import com.az.studyforge.generation.entity.QuizSetStatus;
import com.az.studyforge.generation.service.QuizSetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuizSetController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class QuizSetControllerTest {

    private static final String BASE = "/api/quiz-sets";

    @Autowired MockMvc mockMvc;

    @MockitoBean QuizSetService quizSetService;
    @MockitoBean JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID quizSetId = UUID.randomUUID();

    private RequestPostProcessor authenticated() {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("role", "USER"));
    }

    private QuizSetResponse queued() {
        return new QuizSetResponse(quizSetId, documentId, "Chapter 05", QuestionType.MCQ, 10, OutputLanguage.VI,
                QuizSetStatus.QUEUED, 0, 0, 0, null, Instant.parse("2026-10-22T03:00:00Z"));
    }

    private String body(String documentId, String type, String count, String language, String title) {
        StringBuilder sb = new StringBuilder("{");
        if (documentId != null) sb.append("\"documentId\":\"").append(documentId).append("\",");
        if (type != null) sb.append("\"questionType\":\"").append(type).append("\",");
        if (count != null) sb.append("\"count\":").append(count).append(',');
        if (language != null) sb.append("\"language\":\"").append(language).append("\",");
        if (title != null) sb.append("\"title\":\"").append(title).append("\",");
        if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
        return sb.append('}').toString();
    }

    private String validBody() {
        return body(documentId.toString(), "MCQ", "10", "VI", null);
    }

    // ===== POST =====

    // C1
    @Test
    void create_valid_returns202WithLocation() throws Exception {
        when(quizSetService.create(eq(userId), any())).thenReturn(queued());

        mockMvc.perform(post(BASE).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, BASE + "/" + quizSetId))
                .andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.result.id").value(quizSetId.toString()))
                .andExpect(jsonPath("$.result.status").value("QUEUED"))
                .andExpect(jsonPath("$.result.cacheKey").doesNotExist());

        verify(quizSetService).create(userId,
                new CreateQuizSetRequest(documentId, QuestionType.MCQ, 10, OutputLanguage.VI, null));
    }

    // C2: thiếu field / sai giới hạn → 400 kèm tên field, không gọi service
    @ParameterizedTest(name = "{0}")
    @CsvSource(nullValues = "NULL", value = {
            "missing documentId, NULL, MCQ,  10, VI,   documentId",
            "missing type,       DOC,  NULL, 10, VI,   questionType",
            "missing count,      DOC,  MCQ,  NULL, VI, count",
            "count 0,            DOC,  MCQ,  0,  VI,   count",
            "count 51,           DOC,  MCQ,  51, VI,   count",
            "missing language,   DOC,  MCQ,  10, NULL, language",
    })
    void create_invalidField_returns400(String caseName, String doc, String type, String count,
                                        String language, String field) throws Exception {
        String docValue = "DOC".equals(doc) ? documentId.toString() : doc;

        mockMvc.perform(post(BASE).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content(body(docValue, type, count, language, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details." + field).exists());

        verifyNoInteractions(quizSetService);
    }

    // C3
    @Test
    void create_titleTooLong_returns400() throws Exception {
        mockMvc.perform(post(BASE).with(authenticated()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(documentId.toString(), "MCQ", "10", "VI", "x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").exists());

        verifyNoInteractions(quizSetService);
    }

    // C4: giá trị enum lạ → Jackson không parse được → 400 MALFORMED_REQUEST
    @ParameterizedTest
    @CsvSource({"TRUE_FALSE, VI", "MCQ, FR"})
    void create_unknownEnum_returns400(String type, String language) throws Exception {
        mockMvc.perform(post(BASE).with(authenticated()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(documentId.toString(), type, "10", language, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));

        verifyNoInteractions(quizSetService);
    }

    // C5: lỗi nghiệp vụ từ service được map đúng HTTP status
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ErrorCode.class,
            names = {"INVALID_QUESTION_COUNT", "DOCUMENT_NOT_FOUND", "DOCUMENT_NOT_READY", "ACCOUNT_LOCKED"})
    void create_serviceError_mappedToStatus(ErrorCode code) throws Exception {
        when(quizSetService.create(eq(userId), any())).thenThrow(new AppException(code));

        mockMvc.perform(post(BASE).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().is(code.getStatus().value()))
                .andExpect(jsonPath("$.errorCode").value(code.name()));
    }

    // C6: hết quota → 429 kèm thông tin cho frontend hiển thị "thử lại sau 0:00"
    @Test
    void create_quotaExceeded_returns429WithDetails() throws Exception {
        when(quizSetService.create(eq(userId), any())).thenThrow(new AppException(ErrorCode.QUOTA_EXCEEDED,
                "You have used all 5 generations for today",
                Map.of("dailyQuota", 5, "resetsAt", "2026-10-22T17:00:00Z"), null));

        mockMvc.perform(post(BASE).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("You have used all 5 generations for today"))
                .andExpect(jsonPath("$.details.dailyQuota").value(5))
                .andExpect(jsonPath("$.details.resetsAt").value("2026-10-22T17:00:00Z"));
    }

    // C7
    @Test
    void create_noToken_returns401() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(quizSetService);
    }

    // ===== GET /{id} =====

    // C8
    @Test
    void get_own_returns200() throws Exception {
        when(quizSetService.get(userId, quizSetId)).thenReturn(queued());

        mockMvc.perform(get(BASE + "/" + quizSetId).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(quizSetId.toString()))
                .andExpect(jsonPath("$.result.requestedCount").value(10))
                .andExpect(jsonPath("$.result.generatedCount").value(0));
    }

    // C9
    @Test
    void get_notFound_returns404() throws Exception {
        when(quizSetService.get(userId, quizSetId)).thenThrow(new AppException(ErrorCode.QUIZ_SET_NOT_FOUND));

        mockMvc.perform(get(BASE + "/" + quizSetId).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUIZ_SET_NOT_FOUND"));
    }

    // C10
    @Test
    void get_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/abc").with(authenticated()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(quizSetService);
    }
}
