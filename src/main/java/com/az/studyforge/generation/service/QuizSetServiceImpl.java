package com.az.studyforge.generation.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.document.dto.DocumentForGeneration;
import com.az.studyforge.document.service.DocumentService;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.dto.CreateQuizSetRequest;
import com.az.studyforge.generation.dto.QuizSetResponse;
import com.az.studyforge.generation.dto.QuotaResponse;
import com.az.studyforge.generation.entity.GenerationJob;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.llm.promt.PromptBuilder;
import com.az.studyforge.generation.policy.QuestionLimitPolicy;
import com.az.studyforge.generation.repository.GenerationJobRepository;
import com.az.studyforge.generation.repository.QuizSetRepository;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizSetServiceImpl implements QuizSetService {

    private static final int MAX_TITLE = 200;

    private final UserService userService;
    private final DocumentService documentService;
    private final QuestionLimitPolicy limitPolicy;
    private final QuizSetRepository quizSetRepository;
    private final GenerationJobRepository jobRepository;
    private final GenerationProperties props;
    private final Clock clock;

    @Override
    @Transactional
    public QuizSetResponse create(UUID userId, CreateQuizSetRequest req) {
        User user = userService.lockActiveUser(userId);                                    // ①
        DocumentForGeneration doc = documentService.getForGeneration(userId, req.documentId());   // ②

        if (!limitPolicy.isAllowed(req.questionType(), req.count(), doc.wordCount())) {    // ③
            throw new AppException(ErrorCode.INVALID_QUESTION_COUNT);
        }
        checkQuota(user);                                                                   // ④

        // E4: đã có bộ trên tài liệu này → bộ mới phải KHÁC: bỏ cache, tránh lặp câu của các bộ cũ
        long previousSets = quizSetRepository.countByUserIdAndDocumentIdAndDeletedAtIsNull(userId, doc.id());
        boolean fresh = previousSets > 0;

        QuizSet set = quizSetRepository.save(QuizSet.builder()                              // ⑥
                .userId(userId)
                .documentId(doc.id())
                .title(titleOf(req.title(), doc.originalFilename(), previousSets))
                .useCache(!fresh)
                .questionType(req.questionType())
                .requestedCount(req.count())
                .language(req.language())
                .cacheKey(cacheKey(doc, req))                                               // ⑤
                .promptVersion(PromptBuilder.PROMPT_VERSION)
                .build());

        jobRepository.save(GenerationJob.builder()
                .quizSetId(set.getId())
                .nextRunAt(clock.instant())
                .build());

        return QuizSetResponse.from(set);
    }

    @Override
    @Transactional(readOnly = true)
    public QuizSetResponse get(UUID userId, UUID quizSetId) {
        return quizSetRepository.findByIdAndUserIdAndDeletedAtIsNull(quizSetId, userId)
                .map(QuizSetResponse::from)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_SET_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaResponse quota(UUID userId) {
        return usage(userService.getActiveUser(userId));
    }

    /** Đếm cả bộ đã xoá: xoá bộ đề không hoàn lại lượt. */
    private QuotaResponse usage(User user) {
        ZoneId zone = props.quotaZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant startOfDay = today.atStartOfDay(zone).toInstant();

        int usedToday = (int) quizSetRepository.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), startOfDay);
        int remaining = Math.max(0, user.getDailyQuota() - usedToday);
        return new QuotaResponse(user.getDailyQuota(), usedToday, remaining,
                today.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private void checkQuota(User user) {
        QuotaResponse quota = usage(user);
        if (quota.remaining() <= 0) {
            throw new AppException(ErrorCode.QUOTA_EXCEEDED,
                    "You have used all %d generations for today".formatted(quota.dailyQuota()),
                    Map.of("dailyQuota", quota.dailyQuota(), "resetsAt", quota.resetsAt().toString()),
                    null);
        }
    }

    static String cacheKey(DocumentForGeneration doc, CreateQuizSetRequest req) {
        return HashUtils.sha256Hex(String.join("|",
                doc.fileHash(), req.questionType().name(), String.valueOf(req.count()),
                req.language().name(), PromptBuilder.PROMPT_VERSION));
    }

    static String titleOf(String requested, String filename) {
        return titleOf(requested, filename, 0);
    }

    /** Không nhập tiêu đề và đã có N bộ trên tài liệu → "Chapter 05 (N+1)". */
    static String titleOf(String requested, String filename, long previousSets) {
        if (requested != null && !requested.isBlank()) return cut(requested.strip(), MAX_TITLE);

        String base = filename.replaceFirst("\\.[^.]+$", "");
        if (previousSets == 0) return cut(base, MAX_TITLE);
        String suffix = " (" + (previousSets + 1) + ")";
        return cut(base, MAX_TITLE - suffix.length()) + suffix;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
