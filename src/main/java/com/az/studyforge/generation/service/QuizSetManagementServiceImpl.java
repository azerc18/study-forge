package com.az.studyforge.generation.service;

import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.generation.dto.OptionResponse;
import com.az.studyforge.generation.dto.QuestionResponse;
import com.az.studyforge.generation.dto.QuizSetResponse;
import com.az.studyforge.generation.entity.Question;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.entity.QuizSetStatus;
import com.az.studyforge.generation.repository.QuestionRepository;
import com.az.studyforge.generation.repository.QuizSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizSetManagementServiceImpl implements QuizSetManagementService {

    private static final int MAX_PAGE_SIZE = 50;

    private final QuizSetRepository quizSetRepository;
    private final QuestionRepository questionRepository;
    private final JobQueue jobQueue;
    private final JsonMapper json;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuizSetResponse> list(UUID userId, UUID documentId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<QuizSet> result = documentId == null
                ? quizSetRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : quizSetRepository.findByUserIdAndDocumentIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId, documentId, pageable);
        return PageResponse.from(result.map(QuizSetResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionResponse> getQuestions(UUID userId, UUID quizSetId) {
        QuizSet set = findOwned(userId, quizSetId);
        if (set.getStatus() != QuizSetStatus.READY && set.getStatus() != QuizSetStatus.PARTIAL) {
            throw new AppException(ErrorCode.QUIZ_SET_NOT_READY);
        }
        return questionRepository.findWithOptionsByQuizSetId(quizSetId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public QuizSetResponse rename(UUID userId, UUID quizSetId, String title) {
        QuizSet set = findOwned(userId, quizSetId);
        set.setTitle(title.strip());
        return QuizSetResponse.from(set);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID quizSetId) {
        QuizSet set = findOwned(userId, quizSetId);
        set.setDeletedAt(clock.instant());
        jobQueue.cancelQueued(set.getId());
    }

    @Override
    @Transactional
    public QuizSetResponse retry(UUID userId, UUID quizSetId) {
        QuizSet set = findOwned(userId, quizSetId);
        if (set.getStatus() != QuizSetStatus.FAILED || !jobQueue.requeueFailed(set.getId())) {
            throw new AppException(ErrorCode.QUIZ_SET_NOT_RETRYABLE);
        }
        set.setStatus(QuizSetStatus.QUEUED);
        set.setErrorMessage(null);
        return QuizSetResponse.from(set);
    }

    private QuizSet findOwned(UUID userId, UUID quizSetId) {
        return quizSetRepository.findByIdAndUserIdAndDeletedAtIsNull(quizSetId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_SET_NOT_FOUND));
    }

    private QuestionResponse toResponse(Question q) {
        List<OptionResponse> options = q.getOptions().stream()
                .map(o -> new OptionResponse(o.getLabel(), o.getContent(), o.isCorrect()))
                .toList();
        List<String> rubric = q.getRubric() == null ? List.of() : List.of(json.readValue(q.getRubric(), String[].class));

        return new QuestionResponse(q.getId(), q.getOrderIndex(), q.getType(), q.getContent(), options,
                q.getExplanation(), q.getModelAnswer(), rubric, List.copyOf(q.getSourcePages()));
    }
}
