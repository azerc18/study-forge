package com.az.studyforge.generation.policy;

import com.az.studyforge.document.config.DocumentProperties;
import com.az.studyforge.generation.domain.QuestionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.az.studyforge.generation.domain.QuestionType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionLimitPolicyTest {

    private static final int SHORT_THRESHOLD = 1500;

    private final QuestionLimitPolicy policy =
            new QuestionLimitPolicy(new DocumentProperties(100, SHORT_THRESHOLD));

    // Q1: ngưỡng là "nhỏ hơn" 1500, không phải "nhỏ hơn hoặc bằng"
    @Test
    void isShort_justBelowThreshold_true() {
        assertThat(policy.isShort(SHORT_THRESHOLD - 1)).isTrue();
    }

    @Test
    void isShort_atThreshold_false() {
        assertThat(policy.isShort(SHORT_THRESHOLD)).isFalse();
    }

    @Test
    void isShort_zeroWords_true() {
        assertThat(policy.isShort(0)).isTrue();
    }

    // Q2
    @Test
    void allowedCounts_shortDocument_onlySmallSets() {
        var counts = policy.allowedCounts(820);

        assertThat(counts.get(MCQ)).containsExactly(10);
        assertThat(counts.get(ESSAY)).containsExactly(3);
        assertThat(counts.get(MIXED)).containsExactly(10);
    }

    // Q3
    @Test
    void allowedCounts_normalDocument_allSizes() {
        var counts = policy.allowedCounts(3000);

        assertThat(counts.get(MCQ)).containsExactly(10, 30, 50);
        assertThat(counts.get(ESSAY)).containsExactly(3, 5, 7);
        assertThat(counts.get(MIXED)).containsExactly(10, 30, 50);
    }

    // Q4: EnumMap giữ thứ tự khai báo → JSON luôn ra MCQ, ESSAY, MIXED
    @Test
    void allowedCounts_keysInEnumDeclarationOrder() {
        assertThat(policy.allowedCounts(3000).keySet()).containsExactly(MCQ, ESSAY, MIXED);
        assertThat(policy.allowedCounts(820).keySet()).containsExactly(MCQ, ESSAY, MIXED);
    }

    // Q5: map là static dùng chung → bên ngoài không được sửa được
    @Test
    void allowedCounts_isImmutable() {
        var counts = policy.allowedCounts(3000);

        assertThatThrownBy(() -> counts.put(MCQ, List.of(999)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> counts.get(MCQ).add(999))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // Q6: M3 sẽ dùng hàm này để validate request tạo đề
    @ParameterizedTest(name = "{0} x{1} with {2} words -> {3}")
    @CsvSource({
            "MCQ,   10, 820,  true",
            "MCQ,   30, 820,  false",
            "MCQ,   50, 3000, true",
            "MCQ,   20, 3000, false",
            "ESSAY, 3,  820,  true",
            "ESSAY, 5,  820,  false",
            "ESSAY, 7,  3000, true",
            "ESSAY, 0,  3000, false",
            "MIXED, 10, 820,  true",
            "MIXED, 50, 820,  false",
            "MIXED, 50, 3000, true",
    })
    void isAllowed(QuestionType type, int count, int words, boolean expected) {
        assertThat(policy.isAllowed(type, count, words)).isEqualTo(expected);
    }
}