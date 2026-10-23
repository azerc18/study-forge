package com.az.studyforge.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StartExamRequest(
        @Min(1) @Max(180) Integer timeLimitMinutes,
        Boolean shuffle) {

    public StartExamRequest {
        shuffle = Boolean.TRUE.equals(shuffle);      // thiếu / null → false
    }
}
