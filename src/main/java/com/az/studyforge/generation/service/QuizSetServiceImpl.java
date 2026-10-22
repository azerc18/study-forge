package com.az.studyforge.generation.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.document.dto.DocumentForGeneration;
import com.az.studyforge.document.service.DocumentService;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.dto.CreateQuizSetRequest;
import com.az.studyforge.generation.dto.QuizSetResponse;
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

        QuizSet set = quizSetRepository.save(QuizSet.builder()                              // ⑥
                .userId(userId)
                .documentId(doc.id())
                .title(titleOf(req.title(), doc.originalFilename()))
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

    private void checkQuota(User user) {
        ZoneId zone = props.quotaZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant startOfDay = today.atStartOfDay(zone).toInstant();

        long usedToday = quizSetRepository.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), startOfDay);
        if (usedToday >= user.getDailyQuota()) {
            Instant resetsAt = today.plusDays(1).atStartOfDay(zone).toInstant();
            throw new AppException(ErrorCode.QUOTA_EXCEEDED,
                    "You have used all %d generations for today".formatted(user.getDailyQuota()),
                    Map.of("dailyQuota", user.getDailyQuota(), "resetsAt", resetsAt.toString()),
                    null);
        }
    }

    static String cacheKey(DocumentForGeneration doc, CreateQuizSetRequest req) {
        return HashUtils.sha256Hex(String.join("|",
                doc.fileHash(), req.questionType().name(), String.valueOf(req.count()),
                req.language().name(), PromptBuilder.PROMPT_VERSION));
    }

    static String titleOf(String requested, String filename) {
        String title = requested != null && !requested.isBlank()
                ? requested.strip()
                : filename.replaceFirst("\\.[^.]+$", "");
        return title.length() <= MAX_TITLE ? title : title.substring(0, MAX_TITLE);
    }
}
