package com.az.studyforge.exam.controller;

import com.az.studyforge.common.dto.ApiResponse;
import com.az.studyforge.common.security.CurrentUser;
import com.az.studyforge.exam.dto.ExamResponse;
import com.az.studyforge.exam.dto.ExamSummary;
import com.az.studyforge.exam.dto.StartExamRequest;
import com.az.studyforge.exam.dto.SubmitExamRequest;
import com.az.studyforge.exam.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /** 201 = bài mới, 200 = đang có bài dở nên trả lại bài đó. */
    @PostMapping("/quiz-sets/{quizSetId}/exams")
    public ResponseEntity<ApiResponse<ExamResponse>> start(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID quizSetId,
                                                           @Valid @RequestBody StartExamRequest request) {
        ExamService.Started started = examService.start(CurrentUser.id(jwt), quizSetId, request);
        HttpStatus status = started.resumed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(ApiResponse.success(status, started.exam(), started.resumed() ? "Exam resumed" : "Exam started"));
    }

    @GetMapping("/quiz-sets/{quizSetId}/exams")
    public ApiResponse<List<ExamSummary>> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quizSetId) {
        return ApiResponse.success(examService.history(CurrentUser.id(jwt), quizSetId));
    }

    @GetMapping("/exams/{examId}")
    public ApiResponse<ExamResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId) {
        return ApiResponse.success(examService.get(CurrentUser.id(jwt), examId));
    }

    @PostMapping("/exams/{examId}/submit")
    public ApiResponse<ExamResponse> submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID examId,
                                            @Valid @RequestBody SubmitExamRequest request) {
        return ApiResponse.success(examService.submit(CurrentUser.id(jwt), examId, request));
    }
}
