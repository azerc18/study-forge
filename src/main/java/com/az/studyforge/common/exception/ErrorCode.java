package com.az.studyforge.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // ===== Common =====
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid request"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request body"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),

    // ===== Auth =====
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid Google ID token"),
    GOOGLE_AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is temporarily unavailable"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account is locked"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"),

    QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Daily quota exceeded"),
    ;

    private final HttpStatus status;
    private final String defaultMessage;
}

