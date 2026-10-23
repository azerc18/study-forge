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

    // ===== Quiz set =====
    INVALID_QUESTION_COUNT(HttpStatus.BAD_REQUEST, "This number of questions is not available for this document"),
    QUIZ_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "Quiz set not found"),
    QUIZ_SET_NOT_READY(HttpStatus.CONFLICT, "Questions are not ready yet"),
    QUIZ_SET_NOT_RETRYABLE(HttpStatus.CONFLICT, "Only failed quiz sets can be retried"),

    // ===== Essay feedback =====
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Question not found"),
    NOT_AN_ESSAY_QUESTION(HttpStatus.BAD_REQUEST, "Only essay questions can be graded"),
    FEEDBACK_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Daily grading limit reached"),
    FEEDBACK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The AI grader is busy. Please try again in a moment"),

    // ===== Exam =====
    EXAM_NOT_FOUND(HttpStatus.NOT_FOUND, "Exam not found"),
    NO_MCQ_QUESTIONS(HttpStatus.CONFLICT, "This quiz set has no multiple-choice questions to take as an exam"),
    INVALID_EXAM_ANSWER(HttpStatus.BAD_REQUEST, "Answer does not belong to this exam"),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down and try again shortly"),
    AI_BUDGET_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "The AI service has reached today's limit. Please try again tomorrow"),

    ;

    private final HttpStatus status;
    private final String defaultMessage;
}

