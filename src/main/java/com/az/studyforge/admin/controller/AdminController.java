package com.az.studyforge.admin.controller;

import com.az.studyforge.admin.dto.AdminStats;
import com.az.studyforge.admin.dto.AdminUserRow;
import com.az.studyforge.admin.dto.FailedQuizSetRow;
import com.az.studyforge.admin.dto.UpdateUserRequest;
import com.az.studyforge.admin.service.AdminQuizSetService;
import com.az.studyforge.admin.service.AdminStatsService;
import com.az.studyforge.admin.service.AdminUserService;
import com.az.studyforge.common.dto.ApiResponse;
import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.security.CurrentUser;
import com.az.studyforge.generation.dto.QuizSetResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Phân quyền ADMIN nằm ở SecurityConfig: requestMatchers("/api/admin/**").hasRole("ADMIN"). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService userService;
    private final AdminStatsService statsService;
    private final AdminQuizSetService quizSetService;

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserRow>> users(@RequestParam(required = false) String q,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(userService.list(q, page, size));
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<AdminUserRow> updateUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userService.update(CurrentUser.id(jwt), id, request), "User updated");
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStats> stats(@RequestParam(defaultValue = "14") int days) {
        return ApiResponse.success(statsService.stats(days));
    }

    @GetMapping("/quiz-sets/failed")
    public ApiResponse<PageResponse<FailedQuizSetRow>> failedQuizSets(@RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(quizSetService.failed(page, size));
    }

    @PostMapping("/quiz-sets/{id}/retry")
    public ApiResponse<QuizSetResponse> retry(@PathVariable UUID id) {
        return ApiResponse.success(quizSetService.retry(id), "Quiz set re-queued");
    }
}
