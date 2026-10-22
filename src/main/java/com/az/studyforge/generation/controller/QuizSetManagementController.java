package com.az.studyforge.generation.controller;

import com.az.studyforge.common.dto.ApiResponse;
import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.security.CurrentUser;
import com.az.studyforge.generation.dto.QuestionResponse;
import com.az.studyforge.generation.dto.QuizSetResponse;
import com.az.studyforge.generation.dto.UpdateQuizSetRequest;
import com.az.studyforge.generation.service.QuizSetManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/quiz-sets")
@RequiredArgsConstructor
public class QuizSetManagementController {

    private final QuizSetManagementService service;

    @GetMapping
    public ApiResponse<PageResponse<QuizSetResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID documentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(CurrentUser.id(jwt), documentId, page, size));
    }

    @GetMapping("/{id}/questions")
    public ApiResponse<List<QuestionResponse>> questions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.success(service.getQuestions(CurrentUser.id(jwt), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<QuizSetResponse> rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @Valid @RequestBody UpdateQuizSetRequest request) {
        return ApiResponse.success(service.rename(CurrentUser.id(jwt), id, request.title()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<QuizSetResponse>> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        QuizSetResponse set = service.retry(CurrentUser.id(jwt), id);
        return ResponseEntity.accepted()
                .location(URI.create("/api/quiz-sets/" + set.id()))
                .body(ApiResponse.success(HttpStatus.ACCEPTED, set, "Generation re-queued"));
    }
}
