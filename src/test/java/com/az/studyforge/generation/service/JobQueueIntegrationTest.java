package com.az.studyforge.generation.service;

import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.ClaimedJob;
import com.az.studyforge.generation.entity.GenerationJob;
import com.az.studyforge.generation.entity.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class JobQueueIntegrationTest extends GenerationIntegrationTestBase {

    @Autowired JobQueue queue;

    private UUID userId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        userId = newUser();
        documentId = newDocument(userId, 1, 100);
    }

    private GenerationJob job(JobStatus status, int attempts, Instant nextRunAt) {
        UUID setId = newQuizSet(userId, documentId, QuestionType.MCQ, 10).getId();
        return newJob(setId, status, attempts, nextRunAt);
    }

    private double secondsUntilNextRun(long jobId) {
        Double s = jdbc.queryForObject(
                "SELECT extract(epoch FROM next_run_at - now()) FROM generation_jobs WHERE id = ?", Double.class, jobId);
        return s == null ? Double.NaN : s;
    }

    // J1
    @Test
    void claim_marksRunningAndIncrementsAttempts() {
        GenerationJob j = job(JobStatus.QUEUED, 0, Instant.now().minusSeconds(1));

        Optional<ClaimedJob> claimed = queue.claim("worker-1");

        assertThat(claimed).hasValueSatisfying(c -> {
            assertThat(c.id()).isEqualTo(j.getId());
            assertThat(c.quizSetId()).isEqualTo(j.getQuizSetId());
            assertThat(c.attempts()).isEqualTo(1);
            assertThat(c.maxAttempts()).isEqualTo(3);
        });
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, locked_by, locked_at FROM generation_jobs WHERE id = ?", j.getId());
        assertThat(row.get("status")).isEqualTo("RUNNING");
        assertThat(row.get("locked_by")).isEqualTo("worker-1");
        assertThat(row.get("locked_at")).isNotNull();
    }

    // J2: job chưa đến hạn (đang chờ retry) thì không được lấy
    @Test
    void claim_jobNotYetDue_notClaimed() {
        job(JobStatus.QUEUED, 1, Instant.now().plus(Duration.ofHours(1)));

        assertThat(queue.claim("worker-1")).isEmpty();
    }

    // J3: job không ở trạng thái QUEUED thì không được lấy
    @Test
    void claim_onlyQueuedJobs() {
        job(JobStatus.RUNNING, 1, Instant.now().minusSeconds(60));
        job(JobStatus.DONE, 1, Instant.now().minusSeconds(60));
        job(JobStatus.FAILED, 3, Instant.now().minusSeconds(60));

        assertThat(queue.claim("worker-1")).isEmpty();
    }

    // J4: job đến hạn sớm nhất được lấy trước
    @Test
    void claim_oldestDueFirst() {
        GenerationJob newer = job(JobStatus.QUEUED, 0, Instant.now().minusSeconds(10));
        GenerationJob older = job(JobStatus.QUEUED, 0, Instant.now().minusSeconds(60));

        assertThat(queue.claim("w").map(ClaimedJob::id)).contains(older.getId());
        assertThat(queue.claim("w").map(ClaimedJob::id)).contains(newer.getId());
        assertThat(queue.claim("w")).isEmpty();
    }

    // J5: QUAN TRỌNG NHẤT. 8 luồng tranh nhau 5 job → mỗi job chỉ được lấy đúng 1 lần (SKIP LOCKED)
    @Test
    void claim_concurrentWorkers_neverClaimSameJobTwice() throws Exception {
        Set<Long> expected = new HashSet<>();
        for (int i = 0; i < 5; i++) expected.add(job(JobStatus.QUEUED, 0, Instant.now().minusSeconds(1)).getId());

        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            String workerId = "worker-" + w;
            futures.add(pool.submit(() -> {
                start.await();
                List<Long> mine = new ArrayList<>();
                for (Optional<ClaimedJob> c; (c = queue.claim(workerId)).isPresent(); ) mine.add(c.get().id());
                return mine;
            }));
        }
        start.countDown();

        List<Long> all = new ArrayList<>();
        for (Future<List<Long>> f : futures) all.addAll(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(all).hasSize(5).doesNotHaveDuplicates();
        assertThat(new HashSet<>(all)).isEqualTo(expected);
    }

    // J6: còn lượt → xếp lại hàng đợi, chờ attempts × 2 phút
    @Test
    void failOrRetry_attemptsLeft_requeuedWithBackoff() {
        GenerationJob j = job(JobStatus.RUNNING, 2, Instant.now());

        boolean retry = queue.failOrRetry(new ClaimedJob(j.getId(), j.getQuizSetId(), 2, 3), "LLM down");

        assertThat(retry).isTrue();
        assertThat(jobStatus(j.getId())).isEqualTo("QUEUED");
        assertThat(secondsUntilNextRun(j.getId())).isBetween(3.5 * 60, 4.5 * 60);   // 2 × 2 phút
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT last_error, locked_by, locked_at FROM generation_jobs WHERE id = ?", j.getId());
        assertThat(row.get("last_error")).isEqualTo("LLM down");
        assertThat(row.get("locked_by")).isNull();
        assertThat(row.get("locked_at")).isNull();
    }

    // J7: hết lượt → FAILED
    @Test
    void failOrRetry_lastAttempt_failed() {
        GenerationJob j = job(JobStatus.RUNNING, 3, Instant.now());

        boolean retry = queue.failOrRetry(new ClaimedJob(j.getId(), j.getQuizSetId(), 3, 3), "LLM down");

        assertThat(retry).isFalse();
        assertThat(jobStatus(j.getId())).isEqualTo("FAILED");
    }

    // J8: thông báo lỗi dài bị cắt cho vừa cột VARCHAR(1000)
    @Test
    void failOrRetry_longError_truncated() {
        GenerationJob j = job(JobStatus.RUNNING, 1, Instant.now());

        queue.failOrRetry(new ClaimedJob(j.getId(), j.getQuizSetId(), 1, 3), "x".repeat(5000));

        String error = jdbc.queryForObject("SELECT last_error FROM generation_jobs WHERE id = ?", String.class, j.getId());
        assertThat(error).hasSize(1000);
    }

    // J9: job RUNNING lâu không heartbeat → trả lại hàng đợi; job vừa heartbeat thì giữ nguyên
    @Test
    void requeueStuck_onlyStaleRunningJobs() {
        GenerationJob stale = job(JobStatus.RUNNING, 1, Instant.now());
        GenerationJob alive = job(JobStatus.RUNNING, 1, Instant.now());
        jdbc.update("UPDATE generation_jobs SET locked_at = now() - interval '20 minutes' WHERE id = ?", stale.getId());
        jdbc.update("UPDATE generation_jobs SET locked_at = now() - interval '1 minute' WHERE id = ?", alive.getId());

        int requeued = queue.requeueStuck(Duration.ofMinutes(15));

        assertThat(requeued).isGreaterThanOrEqualTo(1);
        assertThat(jobStatus(stale.getId())).isEqualTo("QUEUED");
        assertThat(jobStatus(alive.getId())).isEqualTo("RUNNING");
        assertThat(queue.claim("w").map(ClaimedJob::id)).contains(stale.getId());   // lấy lại được ngay
    }

    // J10
    @Test
    void heartbeat_refreshesLockedAt() {
        GenerationJob j = job(JobStatus.RUNNING, 1, Instant.now());
        jdbc.update("UPDATE generation_jobs SET locked_at = now() - interval '20 minutes' WHERE id = ?", j.getId());

        queue.heartbeat(j.getId());

        Double age = jdbc.queryForObject(
                "SELECT extract(epoch FROM now() - locked_at) FROM generation_jobs WHERE id = ?", Double.class, j.getId());
        assertThat(age).isLessThan(5.0);
    }

    // J11
    @Test
    void complete_marksDoneAndReleasesLock() {
        GenerationJob j = job(JobStatus.RUNNING, 1, Instant.now());

        queue.complete(j.getId());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, locked_by, locked_at FROM generation_jobs WHERE id = ?", j.getId());
        assertThat(row.get("status")).isEqualTo("DONE");
        assertThat(row.get("locked_by")).isNull();
        assertThat(row.get("locked_at")).isNull();
    }
}
