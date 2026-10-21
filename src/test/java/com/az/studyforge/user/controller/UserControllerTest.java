package com.az.studyforge.user.controller;

import com.az.studyforge.auth.config.SecurityConfig;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class UserControllerTest {

    // Đổi thành "/api/user/me" nếu bạn giữ đường dẫn số ít
    private static final String ME_URL = "/api/users/me";

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;
    @MockitoBean JwtDecoder jwtDecoder;   // cần cho oauth2ResourceServer trong SecurityConfig

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .googleSub("sub-1")
                .email("a@test.com")
                .fullName("Toan")
                .avatarUrl("https://img.test/avatar.png")
                .build();
    }

    /** JWT giả: bỏ qua bước decode, đưa thẳng Jwt có sub = userId vào security context. */
    private RequestPostProcessor authenticatedAs(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("role", "USER"));
    }

    // M1
    @Test
    void me_authenticatedUser_returns200WithProfile() throws Exception {
        when(userService.getActiveUser(userId)).thenReturn(user);

        mockMvc.perform(get(ME_URL).with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.result.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.result.user.email").value("a@test.com"))
                .andExpect(jsonPath("$.result.user.fullName").value("Toan"))
                .andExpect(jsonPath("$.result.user.role").value("USER"))
                .andExpect(jsonPath("$.result.dailyQuota").value(5))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    // M2
    @Test
    void me_noToken_returns401InApiResponseFormat() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(userService);
    }

    // M3
    @Test
    void me_userNotFound_returns404() throws Exception {
        when(userService.getActiveUser(userId)).thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get(ME_URL).with(authenticatedAs(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    // M4
    @Test
    void me_lockedUser_returns403() throws Exception {
        when(userService.getActiveUser(userId)).thenThrow(new AppException(ErrorCode.ACCOUNT_LOCKED));

        mockMvc.perform(get(ME_URL).with(authenticatedAs(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    // M5: token sai → decoder ném lỗi → entry point trả 401 JSON
    @Test
    void me_invalidToken_returns401InApiResponseFormat() throws Exception {
        when(jwtDecoder.decode("abc")).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(userService);
    }

    // M6
    @Test
    void me_usesSubjectClaimAsUserId() throws Exception {
        when(userService.getActiveUser(any())).thenReturn(user);

        mockMvc.perform(get(ME_URL).with(authenticatedAs(userId)))
                .andExpect(status().isOk());

        verify(userService).getActiveUser(userId);
    }
}
