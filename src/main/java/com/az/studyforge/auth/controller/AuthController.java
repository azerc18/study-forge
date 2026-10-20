package com.az.studyforge.auth.controller;

import com.az.studyforge.auth.config.JwtProperties;
import com.az.studyforge.auth.dto.AuthResult;
import com.az.studyforge.auth.dto.ClientInfo;
import com.az.studyforge.auth.dto.GoogleLoginRequest;
import com.az.studyforge.auth.dto.LoginResponse;
import com.az.studyforge.auth.service.AuthService;
import com.az.studyforge.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    public static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest http) {
        ClientInfo clientInfo = new ClientInfo(
                http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());

        AuthResult result = authService.loginWithGoogle(request, clientInfo);

        ApiResponse<LoginResponse> body = ApiResponse.success(LoginResponse.of(result), "Login successful");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(body);
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
    }
}
