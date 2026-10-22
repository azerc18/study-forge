package com.az.studyforge.generation.service;

import com.az.studyforge.generation.entity.ClaimedJob;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JobQueue {

    private static final int MAX_ERROR = 1000;
    private final JdbcTemplate jdbc;

    public Optional<ClaimedJob> claim(String workerId) {
        List<ClaimedJob> rows = jdbc.query("""
                UPDATE generation_jobs
                   SET status = 'RUNNING', locked_by = ?, locked_at = now(),
                       attempts = attempts + 1, updated_at = now()
                 WHERE id = (SELECT id FROM generation_jobs
                              WHERE status = 'QUEUED' AND next_run_at <= now()
                              ORDER BY next_run_at
                              LIMIT 1
                              FOR UPDATE SKIP LOCKED)
                RETURNING id, quiz_set_id, attempts, max_attempts
                """,
                (rs, i) -> new ClaimedJob(rs.getLong("id"), rs.getObject("quiz_set_id", UUID.class),
                        rs.getInt("attempts"), rs.getInt("max_attempts")),
                workerId);
        return rows.stream().findFirst();
    }

    public void heartbeat(long jobId) {
        jdbc.update("UPDATE generation_jobs SET locked_at = now() WHERE id = ? AND status = 'RUNNING'", jobId);
    }

    public void complete(long jobId) {
        jdbc.update("""
                UPDATE generation_jobs
                   SET status = 'DONE', locked_by = NULL, locked_at = NULL, last_error = NULL, updated_at = now()
                 WHERE id = ?""", jobId);
    }

    public boolean failOrRetry(ClaimedJob job, String error) {
        String msg = error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR);
        if (job.attempts() < job.maxAttempts()) {
            jdbc.update("""
                    UPDATE generation_jobs
                       SET status = 'QUEUED', next_run_at = now() + (? * interval '2 minutes'),
                           locked_by = NULL, locked_at = NULL, last_error = ?, updated_at = now()
                     WHERE id = ?""", job.attempts(), msg, job.id());
            return true;
        }
        jdbc.update("""
                UPDATE generation_jobs
                   SET status = 'FAILED', locked_by = NULL, locked_at = NULL, last_error = ?, updated_at = now()
                 WHERE id = ?""", msg, job.id());
        return false;
    }

    public int requeueStuck(Duration timeout) {
        return jdbc.update("""
                UPDATE generation_jobs
                   SET status = 'QUEUED', next_run_at = now(), locked_by = NULL, locked_at = NULL,
                       last_error = 'Worker stopped responding', updated_at = now()
                 WHERE status = 'RUNNING' AND locked_at < now() - (? * interval '1 second')
                """, timeout.toSeconds());
    }

    public void cancelQueued(UUID quizSetId) {
        jdbc.update("""
            UPDATE generation_jobs
               SET status = 'DONE', last_error = 'Cancelled: quiz set deleted', updated_at = now()
             WHERE quiz_set_id = ? AND status = 'QUEUED'""", quizSetId);
    }

    public boolean requeueFailed(UUID quizSetId) {
        return jdbc.update("""
            UPDATE generation_jobs
               SET status = 'QUEUED', attempts = 0, next_run_at = now(),
                   locked_by = NULL, locked_at = NULL, last_error = NULL, updated_at = now()
             WHERE quiz_set_id = ? AND status = 'FAILED'""", quizSetId) == 1;
    }
}
