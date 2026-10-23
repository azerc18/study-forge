package com.az.studyforge.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Field null = giữ nguyên. Dùng kiểu bọc (Integer/Boolean): Jackson 3 không nhận null cho kiểu nguyên thuỷ. */
public record UpdateUserRequest(
        @Min(0) @Max(1000) Integer dailyQuota,
        Boolean locked) {}
