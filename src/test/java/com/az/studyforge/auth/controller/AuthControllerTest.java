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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthControllerTest.TestConfig.class})
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String URL = "/api/auth/google";
    private static final String REFRESH_TOKEN = "refresh-secret-value";

    @TestConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
    }

    @Autowired MockMvc mockMvc;

    @MockitoBean AuthService authService;
    @MockitoBean JwtDecoder jwtDecoder; // cần cho oauth2ResourceServer trong SecurityConfig

    private AuthResult authResult;

    @BeforeEach
    void setUp() {
        UserResponse user = new UserResponse(
                UUID.randomUUID(), "a@test.com", "Toan", "https://img.test/avatar.png", Role.USER);
        authResult = new AuthResult("access-token", 900L, REFRESH_TOKEN, user);
    }

    private ResultActions postLogin(String body) throws Exception {
        return mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void givenLoginFails(ErrorCode code) {
        when(authService.loginWithGoogle(any(), any())).thenThrow(new AppException(code));
    }

    // C1
    @Test
    void google_validRequest_returns200WithBody() throws Exception {
        when(authService.loginWithGoogle(any(), any())).thenReturn(authResult);

        postLogin("{\"idToken\":\"x\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.result.accessToken").value("access-token"))
                .andExpect(jsonPath("$.result.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.result.expiresIn").value(900))
                .andExpect(jsonPath("$.result.user.email").value("a@test.com"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    // C2
    @Test
    void google_validRequest_setsRefreshCookie() throws Exception {
        when(authService.loginWithGoogle(any(), any())).thenReturn(authResult);

        postLogin("{\"idToken\":\"x\"}")
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=" + REFRESH_TOKEN)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=604800")));
    }

    // C3
    @Test
    void google_responseBody_doesNotContainRefreshToken() throws Exception {
        when(authService.loginWithGoogle(any(), any())).thenReturn(authResult);

        postLogin("{\"idToken\":\"x\"}")
                .andExpect(content().string(not(containsString(REFRESH_TOKEN))));
    }

    // C4
    @Test
    void google_blankIdToken_returns400WithDetails() throws Exception {
        postLogin("{\"idToken\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.idToken").exists());
    }

    // C5
    @Test
    void google_missingBody_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    // C6
    @Test
    void google_malformedJson_returns400() throws Exception {
        postLogin("{idToken:")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    // C7
    @Test
    void google_idTokenTooLong_returns400() throws Exception {
        postLogin("{\"idToken\":\"" + "a".repeat(5000) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.idToken").exists());
    }

    // C8
    @Test
    void google_invalidGoogleToken_returns401() throws Exception {
        givenLoginFails(ErrorCode.INVALID_GOOGLE_TOKEN);

        postLogin("{\"idToken\":\"x\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_GOOGLE_TOKEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // C9
    @Test
    void google_lockedAccount_returns403() throws Exception {
        givenLoginFails(ErrorCode.ACCOUNT_LOCKED);

        postLogin("{\"idToken\":\"x\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    // C10
    @Test
    void google_googleUnavailable_returns503() throws Exception {
        givenLoginFails(ErrorCode.GOOGLE_AUTH_UNAVAILABLE);

        postLogin("{\"idToken\":\"x\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_AUTH_UNAVAILABLE"));
    }

    // C11
    @Test
    void google_unexpectedException_returns500WithoutLeakingMessage() throws Exception {
        when(authService.loginWithGoogle(any(), any()))
                .thenThrow(new RuntimeException("SQL secret detail"));

        postLogin("{\"idToken\":\"x\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("SQL secret detail"))));
    }

    // C12
    @Test
    void google_getMethod_returns405() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    // C13
    @Test
    void google_passesUserAgentAndIp() throws Exception {
        when(authService.loginWithGoogle(any(), any())).thenReturn(authResult);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"x\"}")
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.1");
                            return req;
                        }))
                .andExpect(status().isOk());

        ArgumentCaptor<ClientInfo> captor = ArgumentCaptor.forClass(ClientInfo.class);
        verify(authService).loginWithGoogle(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ClientInfo("JUnit", "10.0.0.1"));
    }
}
