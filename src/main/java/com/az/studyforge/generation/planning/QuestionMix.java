package com.az.studyforge.generation.planning;

import com.az.studyforge.generation.domain.QuestionType;

public record QuestionMix(int mcq, int essay) {

    public static QuestionMix of(QuestionType type, int count) {
        return switch (type) {
            case MCQ -> new QuestionMix(count, 0);
            case ESSAY -> new QuestionMix(0, count);
            case MIXED -> {
                int essay = Math.max(1, Math.round(count / 10f));   // 10 → 1, 30 → 3, 50 → 5
                yield new QuestionMix(count - essay, essay);
            }
        };
    }

    public int total() {
        return mcq + essay;
    }
}
