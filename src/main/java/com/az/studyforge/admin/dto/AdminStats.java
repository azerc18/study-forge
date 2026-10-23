package com.az.studyforge.admin.dto;

import java.util.List;

/** daily: cũ → mới (để vẽ biểu đồ); today = phần tử cuối. */
public record AdminStats(
        DailyStat today,
        List<DailyStat> daily,
        long totalUsers,
        double budgetUsd,
        boolean budgetExceeded) {}
