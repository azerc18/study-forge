package com.az.studyforge.exam.controller;

import com.az.studyforge.auth.config.SecurityConfig;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.exam.dto.*;
import com.az.studyforge.exam.entity.ExamStatus;
import com.az.studyforge.exam.service.ExamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExamController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ExamControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ExamService examService;
    @MockitoBean JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();
    private final UUID quizSetId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();

    private static final Instant STARTED = Instant.parse("2026-10-23T02:00:00Z");

    private RequestPostProcessor authenticated() {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("role", "USER"));
    }

    private ExamResponse inProgress() {
        ExamQuestionView q = new ExamQuestionView(questionId, 1, "What is MVC?",
                List.of(new ExamOption("A", "a"), new ExamOption("B", "b"),
                        new ExamOption("C", "c"), new ExamOption("D", "d")),
                "MVC", List.of(3), null, null, null, null);
        return new ExamResponse(examId, quizSetId, "Chapter 05", ExamStatus.IN_PROGRESS, 1, 900,
                STARTED, STARTED.plusSeconds(900), null, null, null, null, List.of(q), List.of());
    }

    private ExamResponse submitted() {
        ExamQuestionView q = new ExamQuestionView(questionId, 1, "What is MVC?",
                List.of(new ExamOption("A", "a"), new ExamOption("B", "b"),
                        new ExamOption("C", "c"), new ExamOption("D", "d")),
                "MVC", List.of(3), "A", "B", false, "Because B");
        return new ExamResponse(examId, quizSetId, "Chapter 05", ExamStatus.SUBMITTED, 1, null,
                STARTED, null, STARTED.plusSeconds(120), 0, new BigDecimal("0.0"), 120L,
                List.of(q), List.of(new TopicResult("MVC", 0, 1)));
    }

    private String submitBody(String label) {
        String l = label == null ? "null" : "\"" + label + "\"";
        return "{\"answers\":[{\"questionId\":\"" + questionId + "\",\"label\":" + l + "}]}";
    }

    // ===== start =====

    @Test
    void start_newExam_returns201_withoutAnswers() throws Exception {
        when(examService.start(eq(userId), eq(quizSetId), any()))
                .thenReturn(new ExamService.Started(inProgress(), false));

        mockMvc.perform(post("/api/quiz-sets/{id}/exams", quizSetId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeLimitMinutes\":15,\"shuffle\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.result.id").value(examId.toString()))
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.deadline").value("2026-10-23T02:15:00Z"))
                .andExpect(jsonPath("$.result.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.result.questions[0].correctLabel").doesNotExist())
                .andExpect(jsonPath("$.result.questions[0].options[0].correct").doesNotExist());

        verify(examService).start(userId, quizSetId, new StartExamRequest(15, true));
    }

    @Test
    void start_existingOpenExam_returns200() throws Exception {
        when(examService.start(eq(userId), eq(quizSetId), any()))
                .thenReturn(new ExamService.Started(inProgress(), true));

        mockMvc.perform(post("/api/quiz-sets/{id}/exams", quizSetId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shuffle\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(examId.toString()));
    }

    @Test
    void start_noTimeLimit_passesNull() throws Exception {
        when(examService.start(eq(userId), eq(quizSetId), any()))
                .thenReturn(new ExamService.Started(inProgress(), false));

        mockMvc.perform(post("/api/quiz-sets/{id}/exams", quizSetId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());

        verify(examService).start(userId, quizSetId, new StartExamRequest(null, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 181})
    void start_invalidTimeLimit_returns400(int minutes) throws Exception {
        mockMvc.perform(post("/api/quiz-sets/{id}/exams", quizSetId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeLimitMinutes\":" + minutes + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(examService);
    }

    @Test
    void start_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/quiz-sets/{id}/exams", quizSetId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(examService);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"QUIZ_SET_NOT_FOUND", "QUIZ_SET_NOT_READY", "NO_MCQ_QUESTIONS"})
    void start_serviceErrors_mappedToStatus(ErrorCode code) throws Exception {
        when(examService.start(eq(userId), eq(quizSetId), any())).thenThrow(new AppException(code));

        mockMvc.perform(post("/api/quiz-sets/{id}/exams", quizSetId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(code.getStatus().value()))
                .andExpect(jsonPath("$.errorCode").value(code.name()));
    }

    // ===== get / history =====

    @Test
    void get_returnsExam() throws Exception {
        when(examService.get(userId, examId)).thenReturn(submitted());

        mockMvc.perform(get("/api/exams/{id}", examId).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.result.score").value(0.0))
                .andExpect(jsonPath("$.result.questions[0].correctLabel").value("B"))
                .andExpect(jsonPath("$.result.topics[0].topic").value("MVC"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(examService.get(userId, examId)).thenThrow(new AppException(ErrorCode.EXAM_NOT_FOUND));

        mockMvc.perform(get("/api/exams/{id}", examId).with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXAM_NOT_FOUND"));
    }

    @Test
    void get_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/exams/{id}", "not-a-uuid").with(authenticated()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(examService);
    }

    @Test
    void history_returnsList() throws Exception {
        when(examService.history(userId, quizSetId)).thenReturn(List.of(
                new ExamSummary(examId, ExamStatus.SUBMITTED, 10, 8, new BigDecimal("8.0"), null,
                        STARTED, STARTED.plusSeconds(300), 300L)));

        mockMvc.perform(get("/api/quiz-sets/{id}/exams", quizSetId).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].score").value(8.0))
                .andExpect(jsonPath("$.result[0].durationSeconds").value(300));
    }

    // ===== submit =====

    @Test
    void submit_valid_returns200() throws Exception {
        when(examService.submit(eq(userId), eq(examId), any())).thenReturn(submitted());

        mockMvc.perform(post("/api/exams/{id}/submit", examId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content(submitBody("A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.correctCount").value(0));

        verify(examService).submit(userId, examId,
                new SubmitExamRequest(List.of(new SubmitExamRequest.Answer(questionId, "A"))));
    }

    @Test
    void submit_nullLabel_meansSkipped_isValid() throws Exception {
        when(examService.submit(eq(userId), eq(examId), any())).thenReturn(submitted());

        mockMvc.perform(post("/api/exams/{id}/submit", examId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content(submitBody(null)))
                .andExpect(status().isOk());
    }

    @Test
    void submit_emptyList_isValid() throws Exception {
        when(examService.submit(eq(userId), eq(examId), any())).thenReturn(submitted());

        mockMvc.perform(post("/api/exams/{id}/submit", examId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[]}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"E", "a", "AB", ""})
    void submit_invalidLabel_returns400(String label) throws Exception {
        mockMvc.perform(post("/api/exams/{id}/submit", examId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content(submitBody(label)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(examService);
    }

    @Test
    void submit_missingAnswers_returns400() throws Exception {
        mockMvc.perform(post("/api/exams/{id}/submit", examId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void submit_missingQuestionId_returns400() throws Exception {
        mockMvc.perform(post("/api/exams/{id}/submit", examId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[{\"label\":\"A\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void submit_tooManyAnswers_returns400() throws Exception {
        StringBuilder sb = new StringBuilder("{\"answers\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"questionId\":\"").append(UUID.randomUUID()).append("\",\"label\":\"A\"}");
        }
        sb.append("]}");

        mockMvc.perform(post("/api/exams/{id}/submit", examId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content(sb.toString()))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"EXAM_NOT_FOUND", "INVALID_EXAM_ANSWER"})
    void submit_serviceErrors_mappedToStatus(ErrorCode code) throws Exception {
        when(examService.submit(eq(userId), eq(examId), any())).thenThrow(new AppException(code));

        mockMvc.perform(post("/api/exams/{id}/submit", examId).with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON).content(submitBody("A")))
                .andExpect(status().is(code.getStatus().value()))
                .andExpect(jsonPath("$.errorCode").value(code.name()));
    }
}
