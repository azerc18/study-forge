package com.az.studyforge.common.dto;

import com.az.studyforge.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        T result,
        Object details
) {
    // ===== Success =====
    public static <T> ApiResponse<T> success(T result) {
        return success(HttpStatus.OK, result, null);
    }

    public static <T> ApiResponse<T> success(T result, String message) {
        return success(HttpStatus.OK, result, message);
    }

    public static <T> ApiResponse<T> success(HttpStatus status, T result, String message) {
        return new ApiResponse<>(Instant.now(), status.value(), null, message, result, null);
    }

    // ===== Error =====
    public static ApiResponse<Void> error(HttpStatus status, String errorCode, String message) {
        return error(status, errorCode, message, null);
    }

    public static ApiResponse<Void> error(HttpStatus status, String errorCode, String message, Object details) {
        return new ApiResponse<>(Instant.now(), status.value(), errorCode, message, null, details);
    }

    public static ApiResponse<Void> error(ErrorCode code, String message, Object details) {
        return new ApiResponse<>(Instant.now(), code.getStatus().value(), code.name(), message, null, details);
    }
}
