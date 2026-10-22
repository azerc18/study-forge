package com.az.studyforge.generation.service;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.repository.DocumentRepository;
import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.dto.CreateQuizSetRequest;
import com.az.studyforge.generation.dto.QuizSetResponse;
import com.az.studyforge.generation.entity.QuizSetStatus;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chạy với Postgres thật. KHÔNG dùng @Transactional ở class: test song song cần mỗi request
 * commit thật thì request sau mới đếm thấy. Dọn dữ liệu bằng cách xoá user (CASCADE xuống mọi bảng con).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class QuizSetCreateIntegrationTest {

    @Autowired QuizSetService quizSetService;
    @Autowired UserRepository userRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> createdUsers = new ArrayList<>();

    private UUID alice;
    private UUID aliceDoc;

    @BeforeEach
    void setUp() {
        alice = saveUser(3);
        aliceDoc = saveReadyDocument(alice, 3000);
    }

    @AfterEach
    void cleanUp() {
        createdUsers.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
    }

    private UUID saveUser(int dailyQuota) {
        String sub = "sub-" + UUID.randomUUID();
        UUID id = userRepository.save(User.builder()
                .googleSub(sub).email(sub + "@test.com").dailyQuota(dailyQuota).build()).getId();
        createdUsers.add(id);
        return id;
    }

    private UUID saveReadyDocument(UUID userId, int words) {
        return documentRepository.save(Document.builder()
                .userId(userId)
                .originalFilename("Chapter 05 - Spring MVC.pptx")
                .fileType(FileType.PPTX)
                .fileSizeBytes(1000)
                .fileHash("a".repeat(64))
                .storageKey("2026/10/" + UUID.randomUUID() + ".pptx")
                .status(DocumentStatus.READY)
                .pageCount(40)
                .wordCount(words)
                .build()).getId();
    }

    private CreateQuizSetRequest mcq10(UUID documentId) {
        return new CreateQuizSetRequest(documentId, QuestionType.MCQ, 10, OutputLanguage.VI, null);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    // I1: quiz_sets và generation_jobs được ghi cùng nhau
    @Test
    void create_persistsQuizSetAndJob() {
        QuizSetResponse res = quizSetService.create(alice, mcq10(aliceDoc));

        assertThat(res.status()).isEqualTo(QuizSetStatus.QUEUED);
        assertThat(res.title()).isEqualTo("Chapter 05 - Spring MVC");
        assertThat(count("SELECT count(*) FROM quiz_sets WHERE id = ? AND status = 'QUEUED'", res.id())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM generation_jobs WHERE quiz_set_id = ? AND status = 'QUEUED'", res.id()))
                .isEqualTo(1);
        assertThat(quizSetService.get(alice, res.id()).id()).isEqualTo(res.id());
    }

    // I2: lỗi thì không để lại dòng nào (rollback cả quiz set lẫn job)
    @Test
    void create_invalidCount_leavesNothingBehind() {
        UUID shortDoc = saveReadyDocument(alice, 800);   // tài liệu ngắn: chỉ được 10 câu

        assertThatThrownBy(() -> quizSetService.create(alice,
                new CreateQuizSetRequest(shortDoc, QuestionType.MCQ, 30, OutputLanguage.VI, null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_QUESTION_COUNT));

        assertThat(count("SELECT count(*) FROM quiz_sets WHERE user_id = ?", alice)).isZero();
    }

    // I3: không tạo được bộ đề từ tài liệu của người khác, và không xem được bộ đề của người khác
    @Test
    void otherUsers_documentAndQuizSet_notAccessible() {
        UUID bob = saveUser(5);
        UUID created = quizSetService.create(alice, mcq10(aliceDoc)).id();

        assertThatThrownBy(() -> quizSetService.create(bob, mcq10(aliceDoc)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND));
        assertThatThrownBy(() -> quizSetService.get(bob, created))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QUIZ_SET_NOT_FOUND));
    }

    // I4: bộ đã xoá vẫn tính vào quota (không thể xoá đi để tạo thêm)
    @Test
    void quota_countsSoftDeletedQuizSets() {
        for (int i = 0; i < 3; i++) quizSetService.create(alice, mcq10(aliceDoc));
        jdbc.update("UPDATE quiz_sets SET deleted_at = now() WHERE user_id = ?", alice);

        assertThatThrownBy(() -> quizSetService.create(alice, mcq10(aliceDoc)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QUOTA_EXCEEDED));
    }

    // I5: QUAN TRỌNG NHẤT. 8 request cùng lúc, quota 3 → đúng 3 thành công.
    //     Bỏ khoá dòng user (lockActiveUser) đi thì test này sẽ đỏ.
    @Test
    void quota_concurrentRequests_neverExceedLimit() throws Exception {
        int requests = 8;   // < pool mặc định 10 connection của Hikari
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            results.add(pool.submit(() -> {
                start.await();                                 // mọi luồng xuất phát cùng lúc
                try {
                    quizSetService.create(alice, mcq10(aliceDoc));
                    return "OK";
                } catch (AppException e) {
                    return e.getErrorCode().name();
                }
            }));
        }
        start.countDown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) outcomes.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(3);
        assertThat(outcomes).filteredOn("QUOTA_EXCEEDED"::equals).hasSize(5);
        assertThat(count("SELECT count(*) FROM quiz_sets WHERE user_id = ?", alice)).isEqualTo(3);
        assertThat(count("""
                SELECT count(*) FROM generation_jobs j JOIN quiz_sets s ON s.id = j.quiz_set_id
                WHERE s.user_id = ?""", alice)).isEqualTo(3);
    }
}
