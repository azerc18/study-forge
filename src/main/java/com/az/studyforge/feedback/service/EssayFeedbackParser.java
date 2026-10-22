package com.az.studyforge.feedback.service;

import com.az.studyforge.feedback.dto.FeedbackDetail;
import com.az.studyforge.feedback.dto.RubricResult;
import com.az.studyforge.feedback.dto.Verdict;
import com.az.studyforge.generation.llm.parse.InvalidLlmOutputException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@RequiredArgsConstructor
public class EssayFeedbackParser {

    public record Parsed(List<RubricResult> rubric, FeedbackDetail detail) {}

    private static final int MAX_ITEMS = 5;
    private final JsonMapper json;

    public Parsed parse(String raw, List<String> rubricPoints) {
        JsonNode root;
        try {
            root = json.readTree(raw.strip().replaceAll("^```(?:json)?\\s*|\\s*```$", ""));
        } catch (JacksonException e) {
            throw new InvalidLlmOutputException("Feedback is not valid JSON", e);
        }
        JsonNode items = root.path("rubric");
        if (!items.isArray()) throw new InvalidLlmOutputException("Missing \"rubric\" array");

        Map<Integer, RubricResult> byPoint = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            JsonNode n = items.get(i);
            int point = n.path("point").isNumber() ? n.path("point").intValue() : -1;
            if (point < 1 || point > rubricPoints.size() || byPoint.containsKey(point)) continue;

            Verdict verdict;
            try {
                verdict = Verdict.valueOf(str(n.path("verdict")).strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidLlmOutputException("Unknown verdict at point " + point);
            }
            byPoint.put(point, new RubricResult(point, rubricPoints.get(point - 1), verdict,
                    blankToNull(str(n.path("comment")))));
        }
        if (byPoint.isEmpty()) throw new InvalidLlmOutputException("No rubric point was assessed");

        List<RubricResult> rubric = new ArrayList<>();
        for (int p = 1; p <= rubricPoints.size(); p++) {
            rubric.add(byPoint.getOrDefault(p, new RubricResult(p, rubricPoints.get(p - 1), Verdict.MISSING, null)));
        }

        FeedbackDetail detail = new FeedbackDetail(
                str(root.path("summary")).strip(),
                list(root.path("corrections")),
                list(root.path("suggestions")),
                blankToNull(str(root.path("improvedAnswer"))));
        return new Parsed(List.copyOf(rubric), detail);
    }

    public static BigDecimal score(List<RubricResult> rubric) {
        double sum = rubric.stream().mapToDouble(r -> r.verdict().weight()).sum();
        return BigDecimal.valueOf(10 * sum / rubric.size()).setScale(1, RoundingMode.HALF_UP);
    }

    private static List<String> list(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < node.size() && out.size() < MAX_ITEMS; i++) {
            String s = str(node.get(i)).strip();
            if (!s.isEmpty()) out.add(s);
        }
        return List.copyOf(out);
    }

    private static String str(JsonNode n) {
        return n != null && n.isValueNode() && !n.isNull() ? n.asString() : "";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
