package com.az.studyforge.admin.service;

import com.az.studyforge.admin.dto.AdminStats;
import com.az.studyforge.admin.dto.DailyStat;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.llm.dto.ProviderUsage;
import com.az.studyforge.generation.service.LlmBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * Thống kê theo ngày (giờ quota-zone). SQL thuần vì gom từ nhiều bảng.
 * Mỗi ngày = [00:00, 00:00 hôm sau) giờ VN, đổi sang timestamptz để so sánh trực tiếp với created_at (dùng được index).
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    static final int MAX_DAYS = 90;

    private static final String COUNTS_SQL = """
            WITH days AS (
                SELECT g::date                                   AS day,
                       g AT TIME ZONE ?::text                        AS start_at,
                       (g + interval '1 day') AT TIME ZONE ?::text   AS end_at
                FROM generate_series(?::date::timestamp, ?::date::timestamp, interval '1 day') AS g
            )
            SELECT d.day,
                   (SELECT count(*) FROM users u
                     WHERE u.created_at >= d.start_at AND u.created_at < d.end_at)                      AS new_users,
                   (SELECT count(*) FROM quiz_sets s
                     WHERE s.created_at >= d.start_at AND s.created_at < d.end_at)                      AS quiz_sets,
                   (SELECT count(*) FROM quiz_sets s
                     WHERE s.status = 'FAILED' AND s.created_at >= d.start_at AND s.created_at < d.end_at) AS failed_sets,
                   (SELECT count(*) FROM exam_attempts e
                     WHERE e.status = 'SUBMITTED' AND e.submitted_at >= d.start_at AND e.submitted_at < d.end_at) AS exams,
                   (SELECT count(*) FROM essay_feedbacks f
                     WHERE f.created_at >= d.start_at AND f.created_at < d.end_at)                      AS essays
            FROM days d
            ORDER BY d.day
            """;

    private static final String TOKENS_SQL = """
            SELECT (created_at AT TIME ZONE ?::text)::date AS day,
                   provider,
                   coalesce(sum(prompt_tokens), 0)        AS prompt_tokens,
                   coalesce(sum(completion_tokens), 0)    AS completion_tokens
            FROM llm_calls
            WHERE created_at >= ?
            GROUP BY 1, 2
            """;

    private final JdbcTemplate jdbc;
    private final LlmBudgetService budget;
    private final GenerationProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminStats stats(int days) {
        int n = Math.clamp(days, 1, MAX_DAYS);
        ZoneId zone = props.quotaZone();
        String tz = zone.getId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate first = today.minusDays(n - 1L);
        Instant from = first.atStartOfDay(zone).toInstant();

        // Token theo ngày + provider → tính tiền bằng đúng công thức của LlmBudgetService
        Map<LocalDate, List<ProviderUsage>> usageByDay = new HashMap<>();
        jdbc.query(TOKENS_SQL, rs -> {
            usageByDay.computeIfAbsent(rs.getObject("day", LocalDate.class), d -> new ArrayList<>())
                    .add(new ProviderUsage(rs.getString("provider"),
                            rs.getLong("prompt_tokens"), rs.getLong("completion_tokens")));
        }, tz, Timestamp.from(from));

        List<DailyStat> daily = jdbc.query(COUNTS_SQL, (rs, i) -> {
            LocalDate day = rs.getObject("day", LocalDate.class);
            List<ProviderUsage> usage = usageByDay.getOrDefault(day, List.of());
            return new DailyStat(day,
                    rs.getInt("new_users"), rs.getInt("quiz_sets"), rs.getInt("failed_sets"),
                    rs.getInt("exams"), rs.getInt("essays"),
                    usage.stream().mapToLong(ProviderUsage::promptTokens).sum(),
                    usage.stream().mapToLong(ProviderUsage::completionTokens).sum(),
                    budget.cost(usage));
        }, tz, tz, first.toString(), today.toString());

        Long totalUsers = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
        LlmBudgetService.BudgetStatus status = budget.status();

        return new AdminStats(daily.getLast(), daily, totalUsers == null ? 0 : totalUsers,
                status.budgetUsd(), status.exceeded());
    }
}
