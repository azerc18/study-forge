package com.az.studyforge.admin.service;

import com.az.studyforge.admin.dto.FailedQuizSetRow;
import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.dto.QuizSetResponse;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.entity.QuizSetStatus;
import com.az.studyforge.generation.repository.QuizSetRepository;
import com.az.studyforge.generation.service.JobQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminQuizSetService {

    static final int MAX_PAGE_SIZE = 100;

    private static final String FAILED_SQL = """
            SELECT s.id, s.title, u.email, s.question_type, s.requested_count, s.error_message,
                   j.last_error, s.created_at
            FROM quiz_sets s
            JOIN users u ON u.id = s.user_id
            LEFT JOIN generation_jobs j ON j.quiz_set_id = s.id
            WHERE s.status = 'FAILED' AND s.deleted_at IS NULL
            ORDER BY s.created_at DESC
            LIMIT ? OFFSET ?
            """;

    private final JdbcTemplate jdbc;
    private final QuizSetRepository quizSetRepository;
    private final JobQueue jobQueue;

    @Transactional(readOnly = true)
    public PageResponse<FailedQuizSetRow> failed(int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.clamp(size, 1, MAX_PAGE_SIZE);

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM quiz_sets WHERE status = 'FAILED' AND deleted_at IS NULL", Long.class);
        List<FailedQuizSetRow> rows = jdbc.query(FAILED_SQL, (rs, i) -> new FailedQuizSetRow(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("email"),
                QuestionType.valueOf(rs.getString("question_type")),
                rs.getInt("requested_count"),
                rs.getString("error_message"),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant()), s, (long) p * s);

        long count = total == null ? 0 : total;
        int totalPages = (int) ((count + s - 1) / s);
        return new PageResponse<>(rows, p, s, count, totalPages, p + 1 < totalPages);
    }

    /** Giống retry của user nhưng không kiểm tra chủ sở hữu. Không tốn lượt (không tạo bộ mới). */
    @Transactional
    public QuizSetResponse retry(UUID quizSetId) {
        QuizSet set = quizSetRepository.findById(quizSetId)
                .filter(q -> q.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_SET_NOT_FOUND));
        if (set.getStatus() != QuizSetStatus.FAILED || !jobQueue.requeueFailed(set.getId())) {
            throw new AppException(ErrorCode.QUIZ_SET_NOT_RETRYABLE);
        }
        set.setStatus(QuizSetStatus.QUEUED);
        set.setErrorMessage(null);
        log.info("Admin retried quiz set {}", quizSetId);
        return QuizSetResponse.from(set);
    }
}
