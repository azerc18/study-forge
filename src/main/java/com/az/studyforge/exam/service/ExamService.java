package com.az.studyforge.exam.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.exam.dto.*;
import com.az.studyforge.exam.entity.ExamAnswer;
import com.az.studyforge.exam.entity.ExamAttempt;
import com.az.studyforge.exam.entity.ExamStatus;
import com.az.studyforge.exam.repository.ExamAnswerRepository;
import com.az.studyforge.exam.repository.ExamAttemptRepository;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.Question;
import com.az.studyforge.generation.entity.QuestionOption;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.entity.QuizSetStatus;
import com.az.studyforge.generation.repository.QuestionRepository;
import com.az.studyforge.generation.repository.QuizSetRepository;
import com.az.studyforge.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamAttemptRepository attemptRepository;
    private final ExamAnswerRepository answerRepository;
    private final QuizSetRepository quizSetRepository;
    private final QuestionRepository questionRepository;
    private final UserService userService;
    private final Clock clock;

    public record Started(ExamResponse exam, boolean resumed) {}

    /** Bắt đầu thi. Đang có bài dở cho bộ này → trả lại bài đó (bỏ qua tuỳ chọn mới). */
    @Transactional
    public Started start(UUID userId, UUID quizSetId, StartExamRequest req) {
        userService.lockActiveUser(userId);              // xếp hàng các request của cùng user → không tạo 2 bài dở
        QuizSet set = ownedSet(userId, quizSetId);
        if (set.getStatus() != QuizSetStatus.READY && set.getStatus() != QuizSetStatus.PARTIAL) {
            throw new AppException(ErrorCode.QUIZ_SET_NOT_READY);
        }

        Optional<ExamAttempt> open = attemptRepository
                .findByUserIdAndQuizSetIdAndStatus(userId, quizSetId, ExamStatus.IN_PROGRESS);
        if (open.isPresent()) return new Started(view(open.get(), set), true);

        List<Question> mcqs = new ArrayList<>(questionRepository.findWithOptionsByQuizSetId(quizSetId).stream()
                .filter(q -> q.getType() == QuestionType.MCQ)
                .toList());
        if (mcqs.isEmpty()) throw new AppException(ErrorCode.NO_MCQ_QUESTIONS);
        if (req.shuffle()) Collections.shuffle(mcqs);

        ExamAttempt attempt = attemptRepository.save(ExamAttempt.builder()
                .quizSetId(quizSetId)
                .userId(userId)
                .timeLimitSeconds(req.timeLimitMinutes() == null ? null : req.timeLimitMinutes() * 60)
                .total(mcqs.size())
                .startedAt(clock.instant())
                .build());

        List<ExamAnswer> rows = new ArrayList<>();
        for (int i = 0; i < mcqs.size(); i++) {
            rows.add(ExamAnswer.builder()
                    .attemptId(attempt.getId())
                    .questionId(mcqs.get(i).getId())
                    .orderIndex(i + 1)
                    .build());
        }
        answerRepository.saveAll(rows);

        return new Started(view(attempt, set.getTitle(), rows, mcqs), false);
    }

    @Transactional(readOnly = true)
    public ExamResponse get(UUID userId, UUID examId) {
        ExamAttempt attempt = attemptRepository.findByIdAndUserId(examId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        QuizSet set = quizSetRepository.findById(attempt.getQuizSetId())
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        return view(attempt, set);
    }

    /**
     * Chấm bài. Nộp lại bài đã nộp → trả kết quả cũ, không chấm lại.
     * Không từ chối bài nộp trễ: frontend tự nộp khi hết giờ; app tự ôn nên không cần chặn gắt.
     */
    @Transactional
    public ExamResponse submit(UUID userId, UUID examId, SubmitExamRequest req) {
        ExamAttempt attempt = attemptRepository.findForUpdate(examId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        QuizSet set = quizSetRepository.findById(attempt.getQuizSetId())
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        if (attempt.getStatus() == ExamStatus.SUBMITTED) return view(attempt, set);

        List<ExamAnswer> rows = answerRepository.findByAttemptIdOrderByOrderIndex(examId);
        Map<UUID, ExamAnswer> byQuestion = rows.stream()
                .collect(Collectors.toMap(ExamAnswer::getQuestionId, Function.identity()));

        for (SubmitExamRequest.Answer a : req.answers()) {       // trùng câu → lấy lần cuối
            ExamAnswer row = byQuestion.get(a.questionId());
            if (row == null) throw new AppException(ErrorCode.INVALID_EXAM_ANSWER);
            row.setSelectedLabel(a.label());
        }

        Map<UUID, Question> questions = questionsById(attempt.getQuizSetId());
        int correct = 0;
        for (ExamAnswer row : rows) {
            String key = correctLabel(questions.get(row.getQuestionId()));
            boolean ok = row.getSelectedLabel() != null && row.getSelectedLabel().equals(key);
            row.setCorrect(ok);
            if (ok) correct++;
        }

        attempt.setCorrectCount(correct);
        attempt.setScore(ExamScoring.score(correct, attempt.getTotal()));
        attempt.setSubmittedAt(clock.instant());
        attempt.setStatus(ExamStatus.SUBMITTED);
        return view(attempt, set.getTitle(), rows, questions);
    }

    @Transactional(readOnly = true)
    public List<ExamSummary> history(UUID userId, UUID quizSetId) {
        ownedSet(userId, quizSetId);
        return attemptRepository.findTop20ByUserIdAndQuizSetIdOrderByStartedAtDesc(userId, quizSetId).stream()
                .map(ExamSummary::from)
                .toList();
    }

    // ---------- helpers ----------

    private QuizSet ownedSet(UUID userId, UUID quizSetId) {
        return quizSetRepository.findByIdAndUserIdAndDeletedAtIsNull(quizSetId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_SET_NOT_FOUND));
    }

    private Map<UUID, Question> questionsById(UUID quizSetId) {
        return questionRepository.findWithOptionsByQuizSetId(quizSetId).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
    }

    private ExamResponse view(ExamAttempt attempt, QuizSet set) {
        return view(attempt, set.getTitle(),
                answerRepository.findByAttemptIdOrderByOrderIndex(attempt.getId()),
                questionsById(attempt.getQuizSetId()));
    }

    private ExamResponse view(ExamAttempt attempt, String title, List<ExamAnswer> rows, List<Question> questions) {
        return view(attempt, title, rows,
                questions.stream().collect(Collectors.toMap(Question::getId, Function.identity())));
    }

    private ExamResponse view(ExamAttempt a, String title, List<ExamAnswer> rows, Map<UUID, Question> questions) {
        boolean done = a.getStatus() == ExamStatus.SUBMITTED;
        List<ExamQuestionView> items = new ArrayList<>();
        List<ExamScoring.Graded> graded = new ArrayList<>();

        for (ExamAnswer row : rows) {
            Question q = questions.get(row.getQuestionId());
            if (q == null) continue;                                 // câu đã bị xoá
            List<ExamOption> options = q.getOptions().stream()
                    .map(o -> new ExamOption(o.getLabel(), o.getContent()))
                    .toList();
            items.add(new ExamQuestionView(q.getId(), row.getOrderIndex(), q.getContent(), options,
                    q.getTopic(), q.getSourcePages(),
                    done ? row.getSelectedLabel() : null,
                    done ? correctLabel(q) : null,
                    done ? row.getCorrect() : null,
                    done ? q.getExplanation() : null));
            if (done) graded.add(new ExamScoring.Graded(q.getTopic(), Boolean.TRUE.equals(row.getCorrect())));
        }

        return new ExamResponse(a.getId(), a.getQuizSetId(), title, a.getStatus(), a.getTotal(),
                a.getTimeLimitSeconds(), a.getStartedAt(), a.deadline(), a.getSubmittedAt(),
                a.getCorrectCount(), a.getScore(), ExamSummary.durationSeconds(a),
                items, done ? ExamScoring.byTopic(graded) : List.of());
    }

    private static String correctLabel(Question q) {
        if (q == null) return null;
        return q.getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .map(QuestionOption::getLabel)
                .findFirst()
                .orElse(null);
    }
}
