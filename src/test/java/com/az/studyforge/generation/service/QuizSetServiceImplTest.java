package com.az.studyforge.generation.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.document.dto.DocumentForGeneration;
import com.az.studyforge.document.service.DocumentService;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.dto.CreateQuizSetRequest;
import com.az.studyforge.generation.dto.QuizSetResponse;
import com.az.studyforge.generation.entity.GenerationJob;
import com.az.studyforge.generation.entity.JobStatus;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.entity.QuizSetStatus;
import com.az.studyforge.generation.llm.promt.PromptBuilder;
import com.az.studyforge.generation.policy.QuestionLimitPolicy;
import com.az.studyforge.generation.repository.GenerationJobRepository;
import com.az.studyforge.generation.repository.QuizSetRepository;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizSetServiceImplTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** 2026-10-22 10:00 giờ Việt Nam. Đầu ngày VN = 2026-10-21T17:00Z. */
    private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");
    private static final Instant START_OF_DAY = Instant.parse("2026-10-21T17:00:00Z");

    @Mock UserService userService;
    @Mock DocumentService documentService;
    @Mock QuestionLimitPolicy limitPolicy;
    @Mock QuizSetRepository quizSetRepository;
    @Mock GenerationJobRepository jobRepository;

    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID quizSetId = UUID.randomUUID();

    private User user;
    private DocumentForGeneration doc;

    @BeforeEach
    void setUp() {
        user = User.builder().id(userId).googleSub("sub").email("a@test.com").dailyQuota(5).build();
        doc = new DocumentForGeneration(documentId, "Chapter 05 - Spring MVC.pptx", 3000, "a".repeat(64));
    }

    private QuizSetServiceImpl serviceAt(Instant now) {
        return new QuizSetServiceImpl(userService, documentService, limitPolicy, quizSetRepository,
                jobRepository, new GenerationProperties(VN), Clock.fixed(now, ZoneOffset.UTC));
    }

    private CreateQuizSetRequest validRequest() {
        return new CreateQuizSetRequest(documentId, QuestionType.MCQ, 10, OutputLanguage.VI, null);
    }

    /** Luồng hợp lệ: user, document, policy đều cho qua; đã dùng {@code used} lượt hôm nay. */
    private void givenValid(long used) {
        when(userService.lockActiveUser(userId)).thenReturn(user);
        when(documentService.getForGeneration(userId, documentId)).thenReturn(doc);
        when(limitPolicy.isAllowed(QuestionType.MCQ, 10, 3000)).thenReturn(true);
        when(quizSetRepository.countByUserIdAndCreatedAtGreaterThanEqual(eq(userId), any())).thenReturn(used);
    }

    private void givenSaveAssignsId() {
        when(quizSetRepository.save(any(QuizSet.class))).thenAnswer(inv -> {
            QuizSet s = inv.getArgument(0);
            s.setId(quizSetId);
            return s;
        });
    }

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // ===== create: luồng thành công =====

    // S1
    @Test
    void create_valid_savesQueuedQuizSetAndJob() {
        givenValid(0);
        givenSaveAssignsId();

        QuizSetResponse res = serviceAt(NOW).create(userId, validRequest());

        var setCaptor = ArgumentCaptor.forClass(QuizSet.class);
        verify(quizSetRepository).save(setCaptor.capture());
        QuizSet saved = setCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getDocumentId()).isEqualTo(documentId);
        assertThat(saved.getTitle()).isEqualTo("Chapter 05 - Spring MVC");
        assertThat(saved.getQuestionType()).isEqualTo(QuestionType.MCQ);
        assertThat(saved.getRequestedCount()).isEqualTo(10);
        assertThat(saved.getLanguage()).isEqualTo(OutputLanguage.VI);
        assertThat(saved.getStatus()).isEqualTo(QuizSetStatus.QUEUED);
        assertThat(saved.getCacheKey()).hasSize(64).matches("[0-9a-f]+");
        assertThat(saved.getPromptVersion()).isEqualTo(PromptBuilder.PROMPT_VERSION);

        var jobCaptor = ArgumentCaptor.forClass(GenerationJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        GenerationJob job = jobCaptor.getValue();
        assertThat(job.getQuizSetId()).isEqualTo(quizSetId);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getNextRunAt()).isEqualTo(NOW);
        assertThat(job.getMaxAttempts()).isEqualTo(3);

        assertThat(res.id()).isEqualTo(quizSetId);
        assertThat(res.status()).isEqualTo(QuizSetStatus.QUEUED);
    }

    // S2: khoá user phải xảy ra TRƯỚC khi đếm quota, nếu không thì khoá vô nghĩa
    @Test
    void create_locksUserBeforeCountingQuota() {
        givenValid(0);
        givenSaveAssignsId();

        serviceAt(NOW).create(userId, validRequest());

        InOrder order = inOrder(userService, quizSetRepository, jobRepository);
        order.verify(userService).lockActiveUser(userId);
        order.verify(quizSetRepository).countByUserIdAndCreatedAtGreaterThanEqual(eq(userId), any());
        order.verify(quizSetRepository).save(any());
        order.verify(jobRepository).save(any());
    }

    // ===== create: các nhánh lỗi =====

    // S3
    @Test
    void create_countNotAllowed_throwsInvalidQuestionCount() {
        when(userService.lockActiveUser(userId)).thenReturn(user);
        when(documentService.getForGeneration(userId, documentId)).thenReturn(doc);
        when(limitPolicy.isAllowed(QuestionType.MCQ, 10, 3000)).thenReturn(false);

        assertErrorCode(() -> serviceAt(NOW).create(userId, validRequest()), ErrorCode.INVALID_QUESTION_COUNT);
        verify(quizSetRepository, never()).save(any());
        verifyNoInteractions(jobRepository);
    }

    // S4
    @Test
    void create_documentNotReady_propagates() {
        when(userService.lockActiveUser(userId)).thenReturn(user);
        when(documentService.getForGeneration(userId, documentId))
                .thenThrow(new AppException(ErrorCode.DOCUMENT_NOT_READY));

        assertErrorCode(() -> serviceAt(NOW).create(userId, validRequest()), ErrorCode.DOCUMENT_NOT_READY);
        verifyNoInteractions(limitPolicy, quizSetRepository, jobRepository);
    }

    // S5
    @Test
    void create_lockedUser_propagates() {
        when(userService.lockActiveUser(userId)).thenThrow(new AppException(ErrorCode.ACCOUNT_LOCKED));

        assertErrorCode(() -> serviceAt(NOW).create(userId, validRequest()), ErrorCode.ACCOUNT_LOCKED);
        verifyNoInteractions(documentService, quizSetRepository, jobRepository);
    }

    // ===== quota =====

    // Q1: đã dùng 4/5 lượt thì vẫn tạo được
    @Test
    void quota_belowLimit_allowed() {
        givenValid(4);
        givenSaveAssignsId();

        serviceAt(NOW).create(userId, validRequest());

        verify(jobRepository).save(any());
    }

    // Q2: đã dùng 5/5 lượt → 429, kèm quota và giờ reset (0:00 giờ VN hôm sau = 17:00Z)
    @Test
    void quota_reached_throwsWithResetTime() {
        givenValid(5);

        assertThatThrownBy(() -> serviceAt(NOW).create(userId, validRequest()))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QUOTA_EXCEEDED);
                    assertThat(e.getDetails()).isEqualTo(
                            Map.of("dailyQuota", 5, "resetsAt", "2026-10-22T17:00:00Z"));
                });
        verify(quizSetRepository, never()).save(any());
        verifyNoInteractions(jobRepository);
    }

    // Q3: mốc đầu ngày tính theo giờ Việt Nam, không theo UTC
    @ParameterizedTest(name = "now={0} -> count since {1}")
    @CsvSource({
            "2026-10-22T03:00:00Z, 2026-10-21T17:00:00Z",   // 10:00 VN
            "2026-10-22T16:59:59Z, 2026-10-21T17:00:00Z",   // 23:59:59 VN, vẫn là ngày 22
            "2026-10-22T17:00:00Z, 2026-10-22T17:00:00Z",   // 00:00 VN ngày 23 → quota mới
            "2026-10-22T00:30:00Z, 2026-10-21T17:00:00Z",   // 07:30 VN; theo UTC sẽ sai thành 2026-10-22T00:00Z
    })
    void quota_countsFromStartOfVietnamDay(Instant now, Instant expectedStart) {
        givenValid(0);
        givenSaveAssignsId();

        serviceAt(now).create(userId, validRequest());

        verify(quizSetRepository).countByUserIdAndCreatedAtGreaterThanEqual(userId, expectedStart);
    }

    // Q4: quota lấy theo từng user (có user được nâng quota)
    @Test
    void quota_usesUsersOwnLimit() {
        user.setDailyQuota(20);
        givenValid(19);
        givenSaveAssignsId();

        serviceAt(NOW).create(userId, validRequest());

        verify(jobRepository).save(any());
    }

    // ===== title =====

    @ParameterizedTest(name = "[{0}] + {1} -> [{2}]")
    @CsvSource(nullValues = "NULL", value = {
            "NULL,             Chapter 05.pptx,   Chapter 05",
            "'   ',            Chapter 05.pptx,   Chapter 05",
            "'  Ôn thi giữa kỳ ', Chapter 05.pptx, Ôn thi giữa kỳ",
            "NULL,             spring.mvc.v2.pdf, spring.mvc.v2",
            "NULL,             no-extension,      no-extension",
    })
    void titleOf(String requested, String filename, String expected) {
        assertThat(QuizSetServiceImpl.titleOf(requested, filename)).isEqualTo(expected);
    }

    @Test
    void titleOf_longFilename_truncatedTo200() {
        String filename = "x".repeat(300) + ".pdf";

        assertThat(QuizSetServiceImpl.titleOf(null, filename)).hasSize(200);
    }

    // ===== cache key =====

    // K1: cùng file + cùng cấu hình → cùng key (dùng được cache, kể cả user khác)
    @Test
    void cacheKey_sameInput_sameKey() {
        var req = validRequest();
        var otherUsersCopy = new DocumentForGeneration(UUID.randomUUID(), "renamed.pptx", 3000, doc.fileHash());

        assertThat(QuizSetServiceImpl.cacheKey(doc, req)).isEqualTo(QuizSetServiceImpl.cacheKey(otherUsersCopy, req));
    }

    // K2: đổi bất kỳ yếu tố nào → key khác. Tiêu đề thì không ảnh hưởng.
    @Test
    void cacheKey_changesWithEachInput() {
        String base = QuizSetServiceImpl.cacheKey(doc, validRequest());
        var otherFile = new DocumentForGeneration(documentId, "x.pptx", 3000, "b".repeat(64));

        assertThat(QuizSetServiceImpl.cacheKey(otherFile, validRequest())).isNotEqualTo(base);
        assertThat(QuizSetServiceImpl.cacheKey(doc,
                new CreateQuizSetRequest(documentId, QuestionType.ESSAY, 10, OutputLanguage.VI, null))).isNotEqualTo(base);
        assertThat(QuizSetServiceImpl.cacheKey(doc,
                new CreateQuizSetRequest(documentId, QuestionType.MCQ, 30, OutputLanguage.VI, null))).isNotEqualTo(base);
        assertThat(QuizSetServiceImpl.cacheKey(doc,
                new CreateQuizSetRequest(documentId, QuestionType.MCQ, 10, OutputLanguage.EN, null))).isNotEqualTo(base);
        assertThat(QuizSetServiceImpl.cacheKey(doc,
                new CreateQuizSetRequest(documentId, QuestionType.MCQ, 10, OutputLanguage.VI, "My title"))).isEqualTo(base);
    }

    // ===== get =====

    // G1
    @Test
    void get_own_returnsResponse() {
        QuizSet set = QuizSet.builder().id(quizSetId).userId(userId).documentId(documentId).title("t")
                .questionType(QuestionType.MCQ).requestedCount(10).language(OutputLanguage.EN)
                .cacheKey("k").promptVersion("v3").build();
        when(quizSetRepository.findByIdAndUserIdAndDeletedAtIsNull(quizSetId, userId)).thenReturn(Optional.of(set));

        QuizSetResponse res = serviceAt(NOW).get(userId, quizSetId);

        assertThat(res.id()).isEqualTo(quizSetId);
        assertThat(res.status()).isEqualTo(QuizSetStatus.QUEUED);
    }

    // G2: không có / của người khác / đã xoá → 404
    @Test
    void get_notFound_throws() {
        when(quizSetRepository.findByIdAndUserIdAndDeletedAtIsNull(quizSetId, userId)).thenReturn(Optional.empty());

        assertErrorCode(() -> serviceAt(NOW).get(userId, quizSetId), ErrorCode.QUIZ_SET_NOT_FOUND);
    }
}
