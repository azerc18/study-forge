package com.az.studyforge.exam.service;

import com.az.studyforge.exam.dto.TopicResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Hàm thuần (không DB) → test dễ. */
public final class ExamScoring {

    static final String NO_TOPIC = "Other";

    private ExamScoring() {}

    /** Thang 10, làm tròn 1 chữ số: 7/9 → 7.8 */
    public static BigDecimal score(int correct, int total) {
        if (total <= 0) return BigDecimal.ZERO.setScale(1);
        return BigDecimal.valueOf(correct).multiply(BigDecimal.TEN)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    public record Graded(String topic, boolean correct) {}

    /**
     * Gộp theo topic (không phân biệt hoa thường, gộp khoảng trắng). Tên hiển thị = cách viết gặp đầu tiên.
     * Sắp xếp: tỉ lệ đúng thấp nhất trước; bằng nhau thì chủ đề nhiều câu hơn trước; cuối cùng theo tên.
     */
    public static List<TopicResult> byTopic(List<Graded> graded) {
        Map<String, String> display = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();          // [correct, total]
        for (Graded g : graded) {
            String name = normalize(g.topic());
            String key = name.toLowerCase(Locale.ROOT);
            display.putIfAbsent(key, name);
            int[] c = counts.computeIfAbsent(key, k -> new int[2]);
            if (g.correct()) c[0]++;
            c[1]++;
        }
        return counts.entrySet().stream()
                .map(e -> new TopicResult(display.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.<TopicResult>comparingDouble(t -> (double) t.correct() / t.total())
                        .thenComparing(TopicResult::total, Comparator.reverseOrder())
                        .thenComparing(TopicResult::topic))
                .toList();
    }

    static String normalize(String topic) {
        if (topic == null || topic.isBlank()) return NO_TOPIC;
        return topic.strip().replaceAll("\\s+", " ");
    }
}
