package com.az.studyforge.admin.dto;

import java.time.LocalDate;

/** Số liệu 1 ngày (theo giờ app.generation.quota-zone). */
public record DailyStat(
        LocalDate date,
        int newUsers,
        int quizSets,
        int failedQuizSets,
        int exams,
        int essayGrades,
        long promptTokens,
        long completionTokens,
        double costUsd) {}
