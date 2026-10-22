package com.az.studyforge.generation.planning;

import com.az.studyforge.generation.domain.QuestionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionMixTest {

    @ParameterizedTest(name = "{0} x{1} -> MCQ {2}, ESSAY {3}")
    @CsvSource({
            "MCQ,   10, 10, 0",
            "MCQ,   50, 50, 0",
            "ESSAY, 3,  0,  3",
            "ESSAY, 7,  0,  7",
            "MIXED, 10, 9,  1",
            "MIXED, 30, 27, 3",
            "MIXED, 50, 45, 5",
    })
    void split(QuestionType type, int count, int mcq, int essay) {
        QuestionMix mix = QuestionMix.of(type, count);

        assertThat(mix.mcq()).isEqualTo(mcq);
        assertThat(mix.essay()).isEqualTo(essay);
        assertThat(mix.total()).isEqualTo(count);
    }
}
