package com.az.studyforge.admin.dto;

import com.az.studyforge.user.entity.Role;

import java.time.Instant;
import java.util.UUID;

/** 1 dòng trong bảng user của admin. usedToday đếm cả bộ đề đã xoá (giống quota). */
public record AdminUserRow(
        UUID id,
        String email,
        String fullName,
        Role role,
        int dailyQuota,
        boolean locked,
        Instant createdAt,
        Instant lastLoginAt,
        long usedToday,
        long quizSetCount) {}
