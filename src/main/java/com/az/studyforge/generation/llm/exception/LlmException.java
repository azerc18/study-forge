package com.az.studyforge.generation.llm.exception;

import lombok.Getter;

import java.time.Duration;

@Getter
public class LlmException extends RuntimeException {
    private final boolean retryable;
    private final Integer httpStatus;
    private final transient Duration retryAfter;

    public LlmException(String message, boolean retryable, Integer httpStatus, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
        this.retryAfter = retryAfter;
    }

    public LlmException(String message, boolean retryable, Integer httpStatus) {
        this(message, retryable, httpStatus, null, null);
    }
}
