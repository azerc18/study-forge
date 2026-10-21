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
import org.springframework.web.bind.annotation.*;

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

        AuthResult result = authService.loginWithGoogle(request, clientInfo(http));

        ApiResponse<LoginResponse> body = ApiResponse.success(LoginResponse.of(result), "Login successful");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest http
    ) {
        AuthResult result = authService.refresh(refreshToken,clientInfo(http));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(ApiResponse.success(LoginResponse.of(result), "Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {

        authService.logout(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    private ClientInfo clientInfo(HttpServletRequest http) {
        return new ClientInfo(http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
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
