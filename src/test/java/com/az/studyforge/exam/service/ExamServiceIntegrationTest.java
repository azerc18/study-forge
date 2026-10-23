package com.az.studyforge.exam.service;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.repository.DocumentRepository;
import com.az.studyforge.exam.dto.*;
import com.az.studyforge.exam.entity.ExamStatus;
import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.Question;
import com.az.studyforge.generation.entity.QuestionOption;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.entity.QuizSetStatus;
import com.az.studyforge.generation.repository.QuestionRepository;
import com.az.studyforge.generation.repository.QuizSetRepository;
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
import org.testcontainers.shaded.org.awaitility.core.ThrowingRunnable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ExamServiceIntegrationTest {

    @Autowired ExamService examService;
    @Autowired UserRepository userRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired QuizSetRepository quizSetRepository;
    @Autowired QuestionRepository questionRepository;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> createdUsers = new ArrayList<>();

    private UUID alice;
    private UUID bob;
    private UUID aliceDoc;

    /** Đáp án đúng của từng câu MCQ trong bộ chuẩn (theo orderIndex 1..4). */
    private static final String[] KEYS = {"A", "B", "C", "D"};
    private static final String[] TOPICS = {"MVC", "MVC", "REST", "JPA"};

    @BeforeEach
    void setUp() {
        alice = saveUser();
        bob = saveUser();
        aliceDoc = saveDocument(alice);
    }

    @AfterEach
    void cleanUp() {
        createdUsers.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        createdUsers.clear();
    }

    // ================= start =================

    @Test
    void start_createsAttemptWithMcqOnly_andHidesAnswers() {
        QuizSet set = mixedSet(alice);                                 // 4 MCQ + 1 essay

        ExamService.Started started = examService.start(alice, set.getId(), new StartExamRequest(15, false));
        ExamResponse exam = started.exam();

        assertThat(started.resumed()).isFalse();
        assertThat(exam.status()).isEqualTo(ExamStatus.IN_PROGRESS);
        assertThat(exam.total()).isEqualTo(4);
        assertThat(exam.quizSetTitle()).isEqualTo("Exam set");
        assertThat(exam.timeLimitSeconds()).isEqualTo(900);
        assertThat(exam.deadline()).isEqualTo(exam.startedAt().plusSeconds(900));
        assertThat(exam.topics()).isEmpty();
        assertThat(exam.questions()).hasSize(4).allSatisfy(q -> {
            assertThat(q.options()).hasSize(4);
            assertThat(q.selectedLabel()).isNull();
            assertThat(q.correctLabel()).isNull();
            assertThat(q.correct()).isNull();
            assertThat(q.explanation()).isNull();
        });
        assertThat(rowCount(exam.id())).isEqualTo(4);
    }

    @Test
    void start_withoutShuffle_keepsQuestionOrder() {
        QuizSet set = mixedSet(alice);

        ExamResponse exam = examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam();

        assertThat(exam.questions()).extracting(ExamQuestionView::content)
                .containsExactly("Q1", "Q2", "Q3", "Q4");
        assertThat(exam.questions()).extracting(ExamQuestionView::number).containsExactly(1, 2, 3, 4);
        assertThat(exam.deadline()).isNull();
        assertThat(exam.timeLimitSeconds()).isNull();
    }

    @Test
    void start_withShuffle_containsSameQuestions_numberedFromOne() {
        QuizSet set = mixedSet(alice);

        ExamResponse exam = examService.start(alice, set.getId(), new StartExamRequest(null, true)).exam();

        assertThat(exam.questions()).extracting(ExamQuestionView::content)
                .containsExactlyInAnyOrder("Q1", "Q2", "Q3", "Q4");
        assertThat(exam.questions()).extracting(ExamQuestionView::number).containsExactly(1, 2, 3, 4);
    }

    @Test
    void start_whileOpen_resumesSameAttempt_ignoringNewOptions() {
        QuizSet set = mixedSet(alice);
        ExamResponse first = examService.start(alice, set.getId(), new StartExamRequest(15, false)).exam();

        ExamService.Started again = examService.start(alice, set.getId(), new StartExamRequest(30, true));

        assertThat(again.resumed()).isTrue();
        assertThat(again.exam().id()).isEqualTo(first.id());
        assertThat(again.exam().timeLimitSeconds()).isEqualTo(900);
        assertThat(attemptCount(alice, set.getId())).isEqualTo(1);
    }

    @Test
    void start_afterSubmit_createsNewAttempt() {
        QuizSet set = mixedSet(alice);
        ExamResponse first = examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam();
        examService.submit(alice, first.id(), new SubmitExamRequest(List.of()));

        ExamService.Started second = examService.start(alice, set.getId(), new StartExamRequest(null, false));

        assertThat(second.resumed()).isFalse();
        assertThat(second.exam().id()).isNotEqualTo(first.id());
    }

    @Test
    void start_concurrentDoubleClick_createsOnlyOneAttempt() throws Exception {
        QuizSet set = mixedSet(alice);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return examService.start(alice, set.getId(), new StartExamRequest(null, true)).exam().id();
                }));
            }
            go.countDown();

            Set<UUID> ids = new HashSet<>();
            for (Future<UUID> f : futures) ids.add(f.get(30, TimeUnit.SECONDS));

            assertThat(ids).hasSize(1);
            assertThat(attemptCount(alice, set.getId())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void start_essayOnlySet_throwsNoMcq() {
        QuizSet set = saveSet(alice, QuestionType.ESSAY, QuizSetStatus.READY);
        saveEssay(set.getId(), 1);

        assertError(() -> examService.start(alice, set.getId(), new StartExamRequest(null, false)),
                ErrorCode.NO_MCQ_QUESTIONS);
        assertThat(attemptCount(alice, set.getId())).isZero();
    }

    @Test
    void start_partialSet_isAllowed() {
        QuizSet set = saveSet(alice, QuestionType.MCQ, QuizSetStatus.PARTIAL);
        saveMcq(set.getId(), 1, "B", "MVC");

        assertThat(examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam().total())
                .isEqualTo(1);
    }

    @Test
    void start_notReadySet_throwsNotReady() {
        for (QuizSetStatus status : List.of(QuizSetStatus.QUEUED, QuizSetStatus.GENERATING, QuizSetStatus.FAILED)) {
            QuizSet set = saveSet(alice, QuestionType.MCQ, status);
            assertError(() -> examService.start(alice, set.getId(), new StartExamRequest(null, false)),
                    ErrorCode.QUIZ_SET_NOT_READY);
        }
    }

    @Test
    void start_otherUsersSet_orDeletedSet_orUnknown_throwsNotFound() {
        QuizSet alicesSet = mixedSet(alice);
        QuizSet deleted = mixedSet(alice);
        deleted.setDeletedAt(Instant.now());
        quizSetRepository.save(deleted);

        assertError(() -> examService.start(bob, alicesSet.getId(), new StartExamRequest(null, false)),
                ErrorCode.QUIZ_SET_NOT_FOUND);
        assertError(() -> examService.start(alice, deleted.getId(), new StartExamRequest(null, false)),
                ErrorCode.QUIZ_SET_NOT_FOUND);
        assertError(() -> examService.start(alice, UUID.randomUUID(), new StartExamRequest(null, false)),
                ErrorCode.QUIZ_SET_NOT_FOUND);
    }

    // ================= submit =================

    @Test
    void submit_gradesAndRevealsAnswers() {
        QuizSet set = mixedSet(alice);
        ExamResponse exam = examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam();
        Map<String, UUID> ids = idsByContent(exam);

        // Q1 đúng, Q2 sai, Q3 đúng, Q4 bỏ trống
        ExamResponse result = examService.submit(alice, exam.id(), new SubmitExamRequest(List.of(
                answer(ids.get("Q1"), "A"),
                answer(ids.get("Q2"), "C"),
                answer(ids.get("Q3"), "C"))));

        assertThat(result.status()).isEqualTo(ExamStatus.SUBMITTED);
        assertThat(result.correctCount()).isEqualTo(2);
        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("5.0"));
        assertThat(result.submittedAt()).isNotNull();
        assertThat(result.durationSeconds()).isNotNull().isGreaterThanOrEqualTo(0L);

        assertThat(result.questions()).extracting(ExamQuestionView::selectedLabel)
                .containsExactly("A", "C", "C", null);
        assertThat(result.questions()).extracting(ExamQuestionView::correctLabel)
                .containsExactly("A", "B", "C", "D");
        assertThat(result.questions()).extracting(ExamQuestionView::correct)
                .containsExactly(true, false, true, false);
        assertThat(result.questions()).allSatisfy(q -> assertThat(q.explanation()).startsWith("Because"));

        // JPA 0/1, MVC 1/2, REST 1/1 → yếu nhất trước
        assertThat(result.topics()).containsExactly(
                new TopicResult("JPA", 0, 1),
                new TopicResult("MVC", 1, 2),
                new TopicResult("REST", 1, 1));

        // GET trả đúng như kết quả lúc nộp
        ExamResponse reloaded = examService.get(alice, exam.id());
        assertThat(reloaded.correctCount()).isEqualTo(2);
        assertThat(reloaded.topics()).isEqualTo(result.topics());
    }

    @Test
    void submit_allCorrect_scoresTen() {
        QuizSet set = mixedSet(alice);
        ExamResponse exam = examService.start(alice, set.getId(), new StartExamRequest(null, true)).exam();
        Map<String, UUID> ids = idsByContent(exam);

        List<SubmitExamRequest.Answer> answers = new ArrayList<>();
        for (int i = 0; i < 4; i++) answers.add(answer(ids.get("Q" + (i + 1)), KEYS[i]));

        ExamResponse result = examService.submit(alice, exam.id(), new SubmitExamRequest(answers));

        assertThat(result.correctCount()).isEqualTo(4);
        assertThat(result.score()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void submit_duplicateAnswerForSameQuestion_lastOneWins() {
        QuizSet set = mixedSet(alice);
        ExamResponse exam = examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam();
        UUID q1 = idsByContent(exam).get("Q1");

        ExamResponse result = examService.submit(alice, exam.id(), new SubmitExamRequest(List.of(
                answer(q1, "D"), answer(q1, "A"))));

        assertThat(result.questions().getFirst().selectedLabel()).isEqualTo("A");
        assertThat(result.correctCount()).isEqualTo(1);
    }

    @Test
    void submit_twice_returnsFirstResult_withoutRegrading() {
        QuizSet set = mixedSet(alice);
        ExamResponse exam = examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam();
        Map<String, UUID> ids = idsByContent(exam);

        ExamResponse first = examService.submit(alice, exam.id(),
                new SubmitExamRequest(List.of(answer(ids.get("Q1"), "A"))));
        ExamResponse second = examService.submit(alice, exam.id(), new SubmitExamRequest(List.of(
                answer(ids.get("Q1"), "A"), answer(ids.get("Q2"), "B"),
                answer(ids.get("Q3"), "C"), answer(ids.get("Q4"), "D"))));

        assertThat(second.correctCount()).isEqualTo(first.correctCount()).isEqualTo(1);
        // Windows: Instant có độ chính xác 100ns, Postgres lưu tới micro giây → so sánh gần đúng
        assertThat(second.submittedAt()).isCloseTo(first.submittedAt(), within(1, ChronoUnit.MILLIS));
    }

    @Test
    void submit_questionNotInExam_throws_andKeepsAttemptOpen() {
        QuizSet set = mixedSet(alice);
        ExamResponse exam = examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam();
        UUID essayId = questionRepository.findWithOptionsByQuizSetId(set.getId()).stream()
                .filter(q -> q.getType() == QuestionType.ESSAY).findFirst().orElseThrow().getId();

        assertError(() -> examService.submit(alice, exam.id(), new SubmitExamRequest(List.of(
                        answer(idsByContent(exam).get("Q1"), "A"), answer(essayId, "A")))),
                ErrorCode.INVALID_EXAM_ANSWER);
        assertError(() -> examService.submit(alice, exam.id(), new SubmitExamRequest(List.of(
                        answer(UUID.randomUUID(), "A")))),
                ErrorCode.INVALID_EXAM_ANSWER);

        // rollback: vẫn đang làm, chưa ghi đáp án nào
        assertThat(examService.get(alice, exam.id()).status()).isEqualTo(ExamStatus.IN_PROGRESS);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM exam_answers WHERE attempt_id = ? AND selected_label IS NOT NULL",
                Integer.class, exam.id())).isZero();
    }

    @Test
    void submit_concurrent_gradesOnce() throws Exception {
        QuizSet set = mixedSet(alice);
        ExamResponse exam = examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam();
        UUID q1 = idsByContent(exam).get("Q1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<ExamResponse> a = pool.submit(() -> {
                go.await();
                return examService.submit(alice, exam.id(), new SubmitExamRequest(List.of(answer(q1, "A"))));
            });
            Future<ExamResponse> b = pool.submit(() -> {
                go.await();
                return examService.submit(alice, exam.id(), new SubmitExamRequest(List.of(answer(q1, "B"))));
            });
            go.countDown();

            ExamResponse ra = a.get(30, TimeUnit.SECONDS);
            ExamResponse rb = b.get(30, TimeUnit.SECONDS);
            assertThat(ra.submittedAt()).isCloseTo(rb.submittedAt(), within(1, ChronoUnit.MILLIS));
            assertThat(ra.correctCount()).isEqualTo(rb.correctCount());
        } finally {
            pool.shutdownNow();
        }
    }

    // ================= ownership / history =================

    @Test
    void otherUser_cannotGetOrSubmit() {
        QuizSet set = mixedSet(alice);
        ExamResponse exam = examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam();

        assertError(() -> examService.get(bob, exam.id()), ErrorCode.EXAM_NOT_FOUND);
        assertError(() -> examService.submit(bob, exam.id(), new SubmitExamRequest(List.of())),
                ErrorCode.EXAM_NOT_FOUND);
        assertError(() -> examService.history(bob, set.getId()), ErrorCode.QUIZ_SET_NOT_FOUND);
        assertThat(examService.get(alice, exam.id()).status()).isEqualTo(ExamStatus.IN_PROGRESS);
    }

    @Test
    void history_newestFirst_onlyThisSet() {
        QuizSet set = mixedSet(alice);
        QuizSet other = mixedSet(alice);

        ExamResponse first = examService.start(alice, set.getId(), new StartExamRequest(null, false)).exam();
        examService.submit(alice, first.id(), new SubmitExamRequest(List.of()));
        ExamResponse second = examService.start(alice, set.getId(), new StartExamRequest(10, false)).exam();
        examService.start(alice, other.getId(), new StartExamRequest(null, false));

        List<ExamSummary> history = examService.history(alice, set.getId());

        assertThat(history).extracting(ExamSummary::id).containsExactly(second.id(), first.id());
        assertThat(history.get(0).status()).isEqualTo(ExamStatus.IN_PROGRESS);
        assertThat(history.get(0).score()).isNull();
        assertThat(history.get(0).durationSeconds()).isNull();
        assertThat(history.get(1).status()).isEqualTo(ExamStatus.SUBMITTED);
        assertThat(history.get(1).score()).isEqualByComparingTo("0.0");
    }

    // ================= helpers =================

    private UUID saveUser() {
        String sub = "sub-" + UUID.randomUUID();
        UUID id = userRepository.save(User.builder().googleSub(sub).email(sub + "@test.com").dailyQuota(5).build())
                .getId();
        createdUsers.add(id);
        return id;
    }

    private UUID saveDocument(UUID userId) {
        return documentRepository.save(Document.builder()
                .userId(userId)
                .originalFilename("lecture.pdf")
                .fileType(FileType.PDF)
                .fileSizeBytes(1000)
                .fileHash("e".repeat(64))
                .storageKey("2026/10/" + UUID.randomUUID() + ".pdf")
                .status(DocumentStatus.READY)
                .pageCount(10)
                .wordCount(3000)
                .build()).getId();
    }

    private QuizSet saveSet(UUID userId, QuestionType type, QuizSetStatus status) {
        UUID docId = userId.equals(alice) ? aliceDoc : saveDocument(userId);
        return quizSetRepository.save(QuizSet.builder()
                .userId(userId).documentId(docId).title("Exam set")
                .questionType(type).requestedCount(5).language(OutputLanguage.VI)
                .status(status)
                .cacheKey(HashUtils.sha256Hex(UUID.randomUUID().toString())).promptVersion("test")
                .build());
    }

    /** READY, MIXED: Q1..Q4 là MCQ (đáp án A,B,C,D; topic MVC,MVC,REST,JPA) + 1 câu tự luận ở cuối. */
    private QuizSet mixedSet(UUID userId) {
        QuizSet set = saveSet(userId, QuestionType.MIXED, QuizSetStatus.READY);
        for (int i = 0; i < 4; i++) saveMcq(set.getId(), i + 1, KEYS[i], TOPICS[i]);
        saveEssay(set.getId(), 5);
        return set;
    }

    private void saveMcq(UUID quizSetId, int order, String key, String topic) {
        Question q = Question.builder()
                .quizSetId(quizSetId).type(QuestionType.MCQ).orderIndex(order)
                .content("Q" + order).explanation("Because " + key)
                .sourcePages(List.of(order)).normalizedHash(HashUtils.sha256Hex("mcq-" + UUID.randomUUID()))
                .topic(topic)
                .build();
        for (String label : KEYS) {
            q.addOption(QuestionOption.builder()
                    .label(label).content("Option " + label).correct(label.equals(key))
                    .build());
        }
        questionRepository.save(q);
    }

    private void saveEssay(UUID quizSetId, int order) {
        questionRepository.save(Question.builder()
                .quizSetId(quizSetId).type(QuestionType.ESSAY).orderIndex(order)
                .content("Essay " + order).modelAnswer("Model").rubric("[\"point\"]")
                .sourcePages(List.of(1)).normalizedHash(HashUtils.sha256Hex("essay-" + UUID.randomUUID()))
                .topic("MVC")
                .build());
    }

    private static Map<String, UUID> idsByContent(ExamResponse exam) {
        Map<String, UUID> map = new HashMap<>();
        exam.questions().forEach(q -> map.put(q.content(), q.id()));
        return map;
    }

    private static SubmitExamRequest.Answer answer(UUID questionId, String label) {
        return new SubmitExamRequest.Answer(questionId, label);
    }

    private int attemptCount(UUID userId, UUID quizSetId) {
        return jdbc.queryForObject("SELECT count(*) FROM exam_attempts WHERE user_id = ? AND quiz_set_id = ?",
                Integer.class, userId, quizSetId);
    }

    private int rowCount(UUID attemptId) {
        return jdbc.queryForObject("SELECT count(*) FROM exam_answers WHERE attempt_id = ?", Integer.class, attemptId);
    }

    private static void assertError(ThrowingRunnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
