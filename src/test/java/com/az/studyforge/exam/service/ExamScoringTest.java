package com.az.studyforge.exam.service;

import com.az.studyforge.exam.dto.TopicResult;
import com.az.studyforge.exam.service.ExamScoring.Graded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamScoringTest {

    // ===== score =====

    @ParameterizedTest(name = "{0}/{1} -> {2}")
    @CsvSource({
            "0, 10, 0.0",
            "10, 10, 10.0",
            "7, 9, 7.8",      // 7.777… làm tròn lên
            "1, 3, 3.3",      // 3.333… làm tròn xuống
            "1, 8, 1.3",      // 1.25 → HALF_UP → 1.3
            "5, 10, 5.0",
            "1, 1, 10.0",
    })
    void score_tenPointScale_oneDecimal(int correct, int total, String expected) {
        assertThat(ExamScoring.score(correct, total)).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void score_zeroTotal_isZeroNotException() {
        assertThat(ExamScoring.score(0, 0)).isEqualTo(new BigDecimal("0.0"));
    }

    // ===== byTopic =====

    @Test
    void byTopic_groupsCaseAndWhitespaceInsensitive_keepsFirstSpelling() {
        List<TopicResult> result = ExamScoring.byTopic(List.of(
                new Graded("View resolvers", true),
                new Graded("  view   RESOLVERS ", false),
                new Graded("VIEW RESOLVERS", true)));

        assertThat(result).containsExactly(new TopicResult("View resolvers", 2, 3));
    }

    @Test
    void byTopic_nullOrBlankTopic_goesToOther() {
        List<TopicResult> result = ExamScoring.byTopic(List.of(
                new Graded(null, true),
                new Graded("   ", false),
                new Graded("", true)));

        assertThat(result).containsExactly(new TopicResult("Other", 2, 3));
    }

    @Test
    void byTopic_weakestFirst() {
        List<TopicResult> result = ExamScoring.byTopic(List.of(
                new Graded("REST", true), new Graded("REST", true),                 // 100%
                new Graded("JPA", false), new Graded("JPA", true),                  // 50%
                new Graded("Servlet", false)));                                     // 0%

        assertThat(result).extracting(TopicResult::topic).containsExactly("Servlet", "JPA", "REST");
    }

    @Test
    void byTopic_sameAccuracy_moreQuestionsFirst_thenByName() {
        List<TopicResult> result = ExamScoring.byTopic(List.of(
                new Graded("Beta", false),                                          // 0/1
                new Graded("Alpha", false),                                         // 0/1
                new Graded("Gamma", false), new Graded("Gamma", false)));           // 0/2

        assertThat(result).extracting(TopicResult::topic).containsExactly("Gamma", "Alpha", "Beta");
    }

    @Test
    void byTopic_empty_returnsEmpty() {
        assertThat(ExamScoring.byTopic(List.of())).isEmpty();
    }

    @Test
    void normalize_collapsesInnerWhitespaceAndTrims() {
        assertThat(ExamScoring.normalize("  Dispatcher\t Servlet \n flow ")).isEqualTo("Dispatcher Servlet flow");
        assertThat(ExamScoring.normalize(null)).isEqualTo("Other");
    }
}
