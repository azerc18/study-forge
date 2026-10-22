package com.az.studyforge.feedback.controller;

import com.az.studyforge.common.dto.ApiResponse;
import com.az.studyforge.common.security.CurrentUser;
import com.az.studyforge.feedback.dto.EssayFeedbackResponse;
import com.az.studyforge.feedback.dto.SubmitEssayRequest;
import com.az.studyforge.feedback.service.EssayFeedbackService;
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
@RequestMapping("/api/questions/{questionId}/essay-feedback")
@RequiredArgsConstructor
public class EssayFeedbackController {

    private final EssayFeedbackService service;

    @PostMapping
    public ResponseEntity<ApiResponse<EssayFeedbackResponse>> submit(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID questionId,
            @Valid @RequestBody SubmitEssayRequest request) {
        EssayFeedbackResponse res = service.submit(CurrentUser.id(jwt), questionId, request.answer());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, res, "Answer graded"));
    }

    @GetMapping
    public ApiResponse<List<EssayFeedbackResponse>> history(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable UUID questionId) {
        return ApiResponse.success(service.history(CurrentUser.id(jwt), questionId));
    }
}
