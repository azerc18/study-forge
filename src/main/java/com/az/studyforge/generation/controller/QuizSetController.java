package com.az.studyforge.generation.controller;

import com.az.studyforge.common.dto.ApiResponse;
import com.az.studyforge.common.security.CurrentUser;
import com.az.studyforge.generation.dto.CreateQuizSetRequest;
import com.az.studyforge.generation.dto.QuizSetResponse;
import com.az.studyforge.generation.service.QuizSetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/quiz-sets")
@RequiredArgsConstructor
public class QuizSetController {

    private final QuizSetService quizSetService;

    @PostMapping
    public ResponseEntity<ApiResponse<QuizSetResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateQuizSetRequest request) {

        QuizSetResponse set = quizSetService.create(CurrentUser.id(jwt), request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/quiz-sets/" + set.id()))
                .body(ApiResponse.success(HttpStatus.ACCEPTED, set, "Generation queued"));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuizSetResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.success(quizSetService.get(CurrentUser.id(jwt), id));
    }
}
