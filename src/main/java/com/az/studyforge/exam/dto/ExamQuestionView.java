package com.az.studyforge.exam.dto;

import java.util.List;
import java.util.UUID;

/**
 * 1 câu trong bài thi. Khi đang làm: selectedLabel/correctLabel/correct/explanation = null (không lộ đáp án).
 * Sau khi nộp: có đủ để xem lại.
 */
public record ExamQuestionView(
        UUID id,
        int number,
        String content,
        List<ExamOption> options,
        String topic,
        List<Integer> sourcePages,
        String selectedLabel,
        String correctLabel,
        Boolean correct,
        String explanation) {}
