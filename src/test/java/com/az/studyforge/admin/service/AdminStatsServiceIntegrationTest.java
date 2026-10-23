package com.az.studyforge.admin.service;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.admin.dto.AdminStats;
import com.az.studyforge.admin.dto.DailyStat;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.repository.DocumentRepository;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.llm.config.LlmProperties;
import com.az.studyforge.generation.service.LlmBudgetService;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Dùng ngày năm 2020 (đồng hồ cố định) để không lẫn với dữ liệu thật của các test khác.
 * "Hôm nay" = 2020-01-15 giờ VN (UTC+7) → 00:00 VN ngày 15 = 2020-01-14T17:00Z.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminStatsServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2020-01-15T03:00:00Z");     // 10:00 VN ngày 15
    private static final LocalDate DAY_13 = LocalDate.of(2020, 1, 13);
    private static final LocalDate DAY_14 = LocalDate.of(2020, 1, 14);
    private static final LocalDate DAY_15 = LocalDate.of(2020, 1, 15);

    @Autowired JdbcTemplate jdbc;
    @Autowired LlmBudgetService budget;
    @Autowired LlmProperties llmProperties;
    @Autowired GenerationProperties generationProperties;
    @Autowired UserRepository userRepository;
    @Autowired DocumentRepository documentRepository;

    private AdminStatsService service;
    private UUID user;
    private UUID doc;

    @BeforeEach
    void setUp() {
        service = new AdminStatsService(jdbc, budget, generationProperties, Clock.fixed(NOW, ZoneOffset.UTC));
        user = userRepository.save(User.builder().googleSub("sub-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@stats.test").dailyQuota(10).build()).getId();
        doc = documentRepository.save(Document.builder()
                .userId(user).originalFilename("a.pdf").fileType(FileType.PDF).fileSizeBytes(1)
                .fileHash("d".repeat(64)).storageKey("x/" + UUID.randomUUID()).status(DocumentStatus.READY)
                .pageCount(1).wordCount(100).build()).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE id = ?", user);                          // CASCADE
        jdbc.update("DELETE FROM llm_calls WHERE created_at < '2021-01-01'");         // log không bị CASCADE
    }

    @Test
    void returnsOneRowPerDay_oldestFirst_withEmptyDaysAsZero() {
        AdminStats stats = service.stats(3);

        assertThat(stats.daily()).extracting(DailyStat::date).containsExactly(DAY_13, DAY_14, DAY_15);
        assertThat(stats.today().date()).isEqualTo(DAY_15);
        assertThat(stats.daily().getFirst()).isEqualTo(new DailyStat(DAY_13, 0, 0, 0, 0, 0, 0, 0, 0.0));
    }

    @Test
    void groupsByVietnamDay_notUtcDay() {
        quizSet("READY", "2020-01-14T16:30:00Z");     // 23:30 VN ngày 14
        quizSet("READY", "2020-01-14T17:30:00Z");     // 00:30 VN ngày 15 (UTC vẫn là ngày 14)
        quizSet("FAILED", "2020-01-15T02:00:00Z");    // 09:00 VN ngày 15

        AdminStats stats = service.stats(3);

        assertThat(day(stats, DAY_14).quizSets()).isEqualTo(1);
        assertThat(day(stats, DAY_15).quizSets()).isEqualTo(2);
        assertThat(day(stats, DAY_15).failedQuizSets()).isEqualTo(1);
        assertThat(day(stats, DAY_14).failedQuizSets()).isZero();
    }

    @Test
    void countsNewUsers_exams_andEssayGrades() {
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", ts("2020-01-15T01:00:00Z"), user);
        UUID set = quizSet("READY", "2020-01-14T05:00:00Z");
        exam(set, "SUBMITTED", "2020-01-15T02:00:00Z");
        exam(set, "IN_PROGRESS", null);                                  // chưa nộp → không tính
        UUID question = essayQuestion(set);
        essay(question, "2020-01-14T10:00:00Z");
        essay(question, "2020-01-14T11:00:00Z");

        AdminStats stats = service.stats(3);

        assertThat(day(stats, DAY_15).newUsers()).isEqualTo(1);
        assertThat(day(stats, DAY_15).exams()).isEqualTo(1);
        assertThat(day(stats, DAY_14).essayGrades()).isEqualTo(2);
        assertThat(stats.totalUsers()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void tokensAndCost_perDay_useConfiguredPrices() {
        LlmProperties.Provider p = llmProperties.providers().getFirst();
        llmCall(p.name(), 1_000_000, 500_000, "2020-01-15T01:00:00Z");
        llmCall(p.name(), null, null, "2020-01-15T01:05:00Z");          // lần gọi lỗi: không có token
        llmCall(p.name(), 10_000, 0, "2020-01-14T16:59:00Z");           // 23:59 VN ngày 14

        AdminStats stats = service.stats(3);

        DailyStat today = day(stats, DAY_15);
        assertThat(today.promptTokens()).isEqualTo(1_000_000);
        assertThat(today.completionTokens()).isEqualTo(500_000);
        assertThat(today.costUsd()).isCloseTo(p.costUsd(1_000_000, 500_000), within(1e-9));
        assertThat(day(stats, DAY_14).promptTokens()).isEqualTo(10_000);
    }

    @Test
    void daysAreClamped() {
        assertThat(service.stats(0).daily()).hasSize(1);
        assertThat(service.stats(500).daily()).hasSize(AdminStatsService.MAX_DAYS);
    }

    // ===== helpers =====

    private static DailyStat day(AdminStats stats, LocalDate date) {
        return stats.daily().stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
    }

    private static Timestamp ts(String iso) {
        return iso == null ? null : Timestamp.from(Instant.parse(iso));
    }

    private UUID quizSet(String status, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO quiz_sets (id, user_id, document_id, title, question_type, requested_count, language,
                                       status, cache_key, prompt_version, created_at)
                VALUES (?, ?, ?, 'S', 'MCQ', 10, 'VI', ?, ?, 'test', ?)
                """, id, user, doc, status, HashUtils.sha256Hex(id.toString()), ts(createdAt));
        return id;
    }

    private void exam(UUID quizSetId, String status, String submittedAt) {
        boolean done = "SUBMITTED".equals(status);
        jdbc.update("""
                INSERT INTO exam_attempts (quiz_set_id, user_id, status, total, correct_count, score, started_at, submitted_at)
                VALUES (?, ?, ?, 10, ?, ?, ?, ?)
                """, quizSetId, user, status, done ? 7 : null, done ? 7.0 : null,
                ts(submittedAt == null ? "2020-01-15T01:00:00Z" : submittedAt), ts(submittedAt));
    }

    private UUID essayQuestion(UUID quizSetId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO questions (id, quiz_set_id, type, order_index, content, normalized_hash)
                VALUES (?, ?, 'ESSAY', 1, 'Q', ?)
                """, id, quizSetId, HashUtils.sha256Hex(id.toString()));
        return id;
    }

    private void essay(UUID questionId, String createdAt) {
        jdbc.update("""
                INSERT INTO essay_feedbacks (question_id, user_id, answer, score, rubric_results, feedback,
                                             model, prompt_version, created_at)
                VALUES (?, ?, 'a', 5.0, '[]'::jsonb, '{}'::jsonb, 'm', 'f2', ?)
                """, questionId, user, ts(createdAt));
    }

    private void llmCall(String provider, Integer prompt, Integer completion, String createdAt) {
        jdbc.update("""
                INSERT INTO llm_calls (provider, model, prompt_tokens, completion_tokens, latency_ms, success, created_at)
                VALUES (?, 'm', ?, ?, 100, ?, ?)
                """, provider, prompt, completion, prompt != null, ts(createdAt));
    }
}
