package com.az.studyforge.admin.controller;

import com.az.studyforge.admin.dto.*;
import com.az.studyforge.admin.service.AdminQuizSetService;
import com.az.studyforge.admin.service.AdminStatsService;
import com.az.studyforge.admin.service.AdminUserService;
import com.az.studyforge.auth.config.SecurityConfig;
import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AdminUserService userService;
    @MockitoBean AdminStatsService statsService;
    @MockitoBean AdminQuizSetService quizSetService;
    @MockitoBean JwtDecoder jwtDecoder;

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private static final Instant T = Instant.parse("2026-10-23T03:00:00Z");

    private RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(adminId.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private AdminUserRow row(int quota, boolean locked) {
        return new AdminUserRow(userId, "a@test.com", "A", Role.USER, quota, locked, T, T, 2, 7);
    }

    // ===== phân quyền =====

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/users", "/api/admin/stats", "/api/admin/quiz-sets/failed"})
    void withoutToken_returns401(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(userService, statsService, quizSetService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/users", "/api/admin/stats", "/api/admin/quiz-sets/failed"})
    void normalUser_returns403(String path) throws Exception {
        mockMvc.perform(get(path).with(as("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        verifyNoInteractions(userService, statsService, quizSetService);
    }

    @Test
    void normalUser_cannotPatchOrRetry() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", userId).with(as("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/quiz-sets/{id}/retry", UUID.randomUUID()).with(as("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService, quizSetService);
    }

    // ===== users =====

    @Test
    void listUsers_admin_returnsPage_withDefaults() throws Exception {
        when(userService.list(null, 0, 20)).thenReturn(new PageResponse<>(List.of(row(10, false)), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/admin/users").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].email").value("a@test.com"))
                .andExpect(jsonPath("$.result.content[0].usedToday").value(2))
                .andExpect(jsonPath("$.result.content[0].quizSetCount").value(7))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    void listUsers_passesSearchAndPaging() throws Exception {
        when(userService.list("toan", 2, 50)).thenReturn(new PageResponse<>(List.of(), 2, 50, 0, 0, false));

        mockMvc.perform(get("/api/admin/users").param("q", "toan").param("page", "2").param("size", "50")
                        .with(as("ADMIN")))
                .andExpect(status().isOk());

        verify(userService).list("toan", 2, 50);
    }

    @Test
    void updateUser_passesAdminIdAndBody() throws Exception {
        when(userService.update(eq(adminId), eq(userId), any())).thenReturn(row(20, true));

        mockMvc.perform(patch("/api/admin/users/{id}", userId).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyQuota\":20,\"locked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.dailyQuota").value(20))
                .andExpect(jsonPath("$.result.locked").value(true));

        verify(userService).update(adminId, userId, new UpdateUserRequest(20, true));
    }

    @Test
    void updateUser_emptyBody_isValid_meansNoChange() throws Exception {
        when(userService.update(eq(adminId), eq(userId), any())).thenReturn(row(10, false));

        mockMvc.perform(patch("/api/admin/users/{id}", userId).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        verify(userService).update(adminId, userId, new UpdateUserRequest(null, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1001})
    void updateUser_invalidQuota_returns400(int quota) throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", userId).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyQuota\":" + quota + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(userService);
    }

    @Test
    void updateUser_cannotLockSelf_returns400() throws Exception {
        when(userService.update(eq(adminId), eq(adminId), any()))
                .thenThrow(new AppException(ErrorCode.CANNOT_MODIFY_SELF));

        mockMvc.perform(patch("/api/admin/users/{id}", adminId).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_MODIFY_SELF"));
    }

    @Test
    void updateUser_unknownUser_returns404() throws Exception {
        when(userService.update(eq(adminId), eq(userId), any())).thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(patch("/api/admin/users/{id}", userId).with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyQuota\":5}"))
                .andExpect(status().isNotFound());
    }

    // ===== stats =====

    @Test
    void stats_defaultDays14() throws Exception {
        DailyStat d = new DailyStat(LocalDate.of(2026, 10, 23), 1, 3, 1, 2, 4, 1000, 500, 0.0123);
        when(statsService.stats(14)).thenReturn(new AdminStats(d, List.of(d), 5, 0.5, false));

        mockMvc.perform(get("/api/admin/stats").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.today.date").value("2026-10-23"))
                .andExpect(jsonPath("$.result.today.quizSets").value(3))
                .andExpect(jsonPath("$.result.daily.length()").value(1))
                .andExpect(jsonPath("$.result.budgetUsd").value(0.5));
    }

    // ===== quiz sets =====

    @Test
    void failedQuizSets_returnsPage() throws Exception {
        when(quizSetService.failed(0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/admin/quiz-sets/failed").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    void retry_notRetryable_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(quizSetService.retry(id)).thenThrow(new AppException(ErrorCode.QUIZ_SET_NOT_RETRYABLE));

        mockMvc.perform(post("/api/admin/quiz-sets/{id}/retry", id).with(as("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("QUIZ_SET_NOT_RETRYABLE"));
    }


}
