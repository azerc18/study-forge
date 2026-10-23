package com.az.studyforge.feedback.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.document.dto.PageContent;
import com.az.studyforge.document.extraction.TextNormalizer;
import com.az.studyforge.document.service.DocumentService;
import com.az.studyforge.feedback.config.FeedbackProperties;
import com.az.studyforge.feedback.dto.EssayFeedbackResponse;
import com.az.studyforge.feedback.dto.FeedbackDetail;
import com.az.studyforge.feedback.dto.RubricResult;
import com.az.studyforge.feedback.entity.EssayFeedback;
import com.az.studyforge.feedback.repository.EssayFeedbackRepository;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.Question;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.llm.LlmClient;
import com.az.studyforge.generation.llm.dto.LlmRequest;
import com.az.studyforge.generation.llm.dto.LlmResult;
import com.az.studyforge.generation.llm.entity.LlmPurpose;
import com.az.studyforge.generation.llm.exception.LlmUnavailableException;
import com.az.studyforge.generation.llm.parse.InvalidLlmOutputException;
import com.az.studyforge.generation.repository.QuestionRepository;
import com.az.studyforge.generation.repository.QuizSetRepository;
import com.az.studyforge.generation.service.LlmBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EssayFeedbackService {

    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_OUTPUT_TOKENS = 1500;

    private final QuestionRepository questionRepository;
    private final QuizSetRepository quizSetRepository;
    private final DocumentService documentService;
    private final EssayFeedbackRepository feedbackRepository;
    private final EssayFeedbackPrompt prompt;
    private final EssayFeedbackParser parser;
    private final LlmClient llmClient;
    private final FeedbackProperties props;
    private final GenerationProperties generationProps;
    private final JsonMapper json;
    private final Clock clock;
    private final LlmBudgetService budget;

    private record EssayContext(Question question, List<String> rubric, OutputLanguage language, UUID documentId) {}

    /** KHÔNG gắn @Transactional: không giữ kết nối DB trong lúc chờ LLM. */
    public EssayFeedbackResponse submit(UUID userId, UUID questionId, String rawAnswer) {
        String answer = rawAnswer.strip();
        EssayContext ctx = loadContext(userId, questionId);   // ①
        budget.check();                                       // hết ngân sách AI → 503 trước khi tính lượt
        checkDailyLimit(userId);                              // ②
        String material = material(ctx);                      // ③

        EssayFeedbackParser.Parsed parsed = null;             // ④ ⑤
        String model = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && parsed == null; attempt++) {
            try {
                LlmResult res = llmClient.complete(new LlmRequest(
                        prompt.systemPrompt(),
                        prompt.userPrompt(ctx.question().getContent(), ctx.rubric(),
                                ctx.question().getModelAnswer(), material, answer, ctx.language()),
                        MAX_OUTPUT_TOKENS, ctx.question().getQuizSetId(), null, LlmPurpose.ESSAY_FEEDBACK));
                parsed = parser.parse(res.content(), ctx.rubric());
                model = res.provider() + "/" + res.model();
            } catch (InvalidLlmOutputException e) {
                log.warn("Essay feedback attempt {} unparseable: {}", attempt, e.getMessage());
            } catch (LlmUnavailableException e) {
                throw new AppException(ErrorCode.FEEDBACK_UNAVAILABLE, e);
            }
        }
        if (parsed == null) throw new AppException(ErrorCode.FEEDBACK_UNAVAILABLE);

        EssayFeedback saved = feedbackRepository.save(EssayFeedback.builder()   // ⑥
                .questionId(questionId).userId(userId).answer(answer)
                .score(EssayFeedbackParser.score(parsed.rubric()))
                .rubricResults(json.writeValueAsString(parsed.rubric()))
                .feedback(json.writeValueAsString(parsed.detail()))
                .model(model).promptVersion(EssayFeedbackPrompt.VERSION)
                .build());
        return toResponse(saved, parsed.rubric(), parsed.detail());
    }

    @Transactional(readOnly = true)
    public List<EssayFeedbackResponse> history(UUID userId, UUID questionId) {
        loadContext(userId, questionId);   // chỉ để kiểm tra quyền và loại câu
        return feedbackRepository.findTop20ByQuestionIdAndUserIdOrderByCreatedAtDesc(questionId, userId).stream()
                .map(f -> toResponse(f,
                        List.of(json.readValue(f.getRubricResults(), RubricResult[].class)),
                        json.readValue(f.getFeedback(), FeedbackDetail.class)))
                .toList();
    }

    private EssayContext loadContext(UUID userId, UUID questionId) {
        Question q = questionRepository.findOwned(questionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        if (q.getType() != QuestionType.ESSAY || q.getRubric() == null) {
            throw new AppException(ErrorCode.NOT_AN_ESSAY_QUESTION);
        }
        QuizSet set = quizSetRepository.findById(q.getQuizSetId()).orElseThrow();
        List<String> rubric = List.of(json.readValue(q.getRubric(), String[].class));
        return new EssayContext(q, rubric, set.getLanguage(), set.getDocumentId());
    }

    private void checkDailyLimit(UUID userId) {
        ZoneId zone = generationProps.quotaZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        long used = feedbackRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                userId, today.atStartOfDay(zone).toInstant());
        if (used >= props.dailyLimit()) {
            throw new AppException(ErrorCode.FEEDBACK_LIMIT_EXCEEDED,
                    "You have used all %d gradings for today".formatted(props.dailyLimit()),
                    Map.of("dailyLimit", props.dailyLimit(),
                            "resetsAt", today.plusDays(1).atStartOfDay(zone).toInstant().toString()),
                    null);
        }
    }

    private String material(EssayContext ctx) {
        Set<Integer> wanted = new HashSet<>(ctx.question().getSourcePages());
        StringBuilder sb = new StringBuilder();
        int words = 0;
        for (PageContent p : documentService.getPagesForGeneration(ctx.documentId())) {
            if (!wanted.contains(p.pageNumber())) continue;
            int w = TextNormalizer.countWords(p.content());
            if (words + w > props.materialMaxWords()) break;
            sb.append("[Page ").append(p.pageNumber()).append("]\n").append(p.content()).append("\n\n");
            words += w;
        }
        return sb.toString().strip();
    }

    private EssayFeedbackResponse toResponse(EssayFeedback f, List<RubricResult> rubric, FeedbackDetail d) {
        return new EssayFeedbackResponse(f.getId(), f.getQuestionId(), f.getAnswer(), f.getScore(), rubric,
                d.summary(), d.corrections(), d.suggestions(), d.improvedAnswer(), f.getCreatedAt());
    }
}
