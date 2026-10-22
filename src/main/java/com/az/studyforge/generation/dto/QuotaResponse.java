package com.az.studyforge.generation.dto;

import java.time.Instant;

/** Lượt tạo bộ đề trong ngày (theo múi giờ app.generation.quota-zone). */
public record QuotaResponse(int dailyQuota, int usedToday, int remaining, Instant resetsAt) {}
