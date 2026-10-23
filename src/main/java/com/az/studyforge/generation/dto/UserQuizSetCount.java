package com.az.studyforge.generation.dto;

import java.util.UUID;

/** Số bộ đề của 1 user: tổng và từ một mốc thời gian (đầu ngày). Đếm cả bộ đã xoá. */
public record UserQuizSetCount(UUID userId, long total, long since) {}
