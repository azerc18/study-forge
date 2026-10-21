package com.az.studyforge.auth.controller;

import com.az.studyforge.auth.config.JwtProperties;
import com.az.studyforge.auth.config.SecurityConfig;
import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.service.AuthService;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.user.dto.UserResponse;
import com.az.studyforge.user.entity.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthControllerRefreshTest.TestConfig.class})
@ActiveProfiles("test")
class AuthControllerRefreshTest {

    private static final String REFRESH_URL = "/api/auth/refresh";
    private static final String LOGOUT_URL = "/api/auth/logout";
    private static final String OLD_TOKEN = "old-refresh-token";
    private static final String NEW_TOKEN = "new-refresh-token";

    @TestConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
    }

    @Autowired MockMvc mockMvc;

    @MockitoBean AuthService authService;
    @MockitoBean JwtDecoder jwtDecoder;

    private AuthResult refreshed;

    @BeforeEach
    void setUp() {
        UserResponse user = new UserResponse(
                UUID.randomUUID(), "a@test.com", "Toan", "https://img.test/avatar.png", Role.USER);
        refreshed = new AuthResult("new-access-token", 900L, NEW_TOKEN, user);
    }

    private static Cookie refreshCookie(String value) {
        return new Cookie(AuthController.REFRESH_COOKIE, value);
    }

    // ===== /refresh =====

    // RC1
    @Test
    void refresh_validCookie_returns200WithNewTokens() throws Exception {
        when(authService.refresh(eq(OLD_TOKEN), any())).thenReturn(refreshed);

        mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(OLD_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token refreshed"))
                .andExpect(jsonPath("$.result.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.result.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.result.expiresIn").value(900))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=" + NEW_TOKEN)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=604800")))
                .andExpect(content().string(not(containsString(NEW_TOKEN))));   // refresh token không nằm trong body
    }

    // RC2
    @Test
    void refresh_noCookie_returns401NotServerError() throws Exception {
        when(authService.refresh(isNull(), any()))
                .thenThrow(new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post(REFRESH_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // RC2b
    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refresh(eq("bad"), any()))
                .thenThrow(new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie("bad")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    // RC5
    @Test
    void refresh_lockedAccount_returns403() throws Exception {
        when(authService.refresh(eq(OLD_TOKEN), any()))
                .thenThrow(new AppException(ErrorCode.ACCOUNT_LOCKED));

        mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(OLD_TOKEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    // RC6
    @Test
    void refresh_passesClientInfo() throws Exception {
        when(authService.refresh(eq(OLD_TOKEN), any())).thenReturn(refreshed);

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(refreshCookie(OLD_TOKEN))
                        .header(HttpHeaders.USER_AGENT, "JUnit"))
                .andExpect(status().isOk());

        ArgumentCaptor<ClientInfo> captor = ArgumentCaptor.forClass(ClientInfo.class);
        verify(authService).refresh(eq(OLD_TOKEN), captor.capture());
        assertThat(captor.getValue().userAgent()).isEqualTo("JUnit");
    }

    // RC7
    @Test
    void refresh_getMethod_returns405() throws Exception {
        mockMvc.perform(get(REFRESH_URL))
                .andExpect(status().isMethodNotAllowed());
    }

    // ===== /logout =====

    // RC3
    @Test
    void logout_withCookie_returns204AndClearsCookie() throws Exception {
        mockMvc.perform(post(LOGOUT_URL).cookie(refreshCookie(OLD_TOKEN)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")));

        verify(authService).logout(OLD_TOKEN);
    }

    // RC4
    @Test
    void logout_withoutCookie_stillReturns204() throws Exception {
        mockMvc.perform(post(LOGOUT_URL))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService).logout(null);
    }

    // RC8: logout không cần access token (đã permitAll)
    @Test
    void logout_withoutAccessToken_isNotBlockedBySecurity() throws Exception {
        mockMvc.perform(post(LOGOUT_URL).cookie(refreshCookie(OLD_TOKEN)))
                .andExpect(status().isNoContent());
    }
}
