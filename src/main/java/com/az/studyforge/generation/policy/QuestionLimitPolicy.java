package com.az.studyforge.generation.policy;

import com.az.studyforge.document.config.DocumentProperties;
import com.az.studyforge.generation.domain.QuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class QuestionLimitPolicy {

    private static final Map<QuestionType, List<Integer>> NORMAL = ordered(Map.of(
            QuestionType.MCQ,   List.of(10, 30, 50),
            QuestionType.ESSAY, List.of(3, 5, 7),
            QuestionType.MIXED, List.of(10, 30, 50)));

    private static final Map<QuestionType, List<Integer>> SHORT = ordered(Map.of(
            QuestionType.MCQ,   List.of(10),
            QuestionType.ESSAY, List.of(3),
            QuestionType.MIXED, List.of(10)));

    private final DocumentProperties documentProperties;

    public boolean isShort(int wordCount) {
        return wordCount < documentProperties.shortDocumentWords();
    }

    public Map<QuestionType, List<Integer>> allowedCounts(int wordCount) {
        return isShort(wordCount) ? SHORT : NORMAL;
    }

    public boolean isAllowed(QuestionType type, int count, int wordCount) {
        return allowedCounts(wordCount).get(type).contains(count);
    }

    private static Map<QuestionType, List<Integer>> ordered(Map<QuestionType, List<Integer>> m) {
        return Collections.unmodifiableMap(new EnumMap<>(m));
    }
}
