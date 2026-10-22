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
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),

    // ===== Document =====
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "File is empty"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 20 MB limit"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF and PPTX files are supported"),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Document not found"),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the file"),
    DOCUMENT_NOT_READY(HttpStatus.CONFLICT, "Document is still being processed"),
    ;

    private final HttpStatus status;
    private final String defaultMessage;
}

