package com.az.studyforge.generation.service;

import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.*;
import com.az.studyforge.generation.llm.exception.LlmUnavailableException;
import com.az.studyforge.generation.llm.model.*;
import com.az.studyforge.generation.llm.parse.InvalidLlmOutputException;
import com.az.studyforge.generation.llm.parse.QuestionNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GenerationJobProcessorIntegrationTest extends GenerationIntegrationTestBase {

    @Autowired GenerationJobProcessor processor;

    private final AtomicInteger seq = new AtomicInteger();

    private UUID userId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        userId = newUser();
        documentId = newDocument(userId, 30, 300);
    }

    // ===== helpers =====

    private static int chunkIndexOf(ChunkSpec spec) {
        return (spec.pageFrom() - 1) / 6;
    }

    private static boolean isTopUp(ChunkSpec spec) {
        return !spec.avoidQuestions().isEmpty();
    }

    private static GeneratedMcq mcq(ChunkSpec spec, String content) {
        return new GeneratedMcq(content, List.of("Option A", "Option B", "Option C", "Option D"), 1,
                "Because B", List.of(spec.pageFrom()), QuestionNormalizer.hash(content));
    }

    private static GeneratedEssay essay(ChunkSpec spec, String content) {
        return new GeneratedEssay(content, "Model answer", List.of("point 1", "point 2"),
                List.of(spec.pageFrom()), QuestionNormalizer.hash(content));
    }

    private ParseResult fresh(ChunkSpec spec, int mcqCount, int essayCount) {
        List<GeneratedQuestion> list = new ArrayList<>();
        for (int i = 0; i < mcqCount; i++) list.add(mcq(spec, "MCQ question " + seq.incrementAndGet()));
        for (int i = 0; i < essayCount; i++) list.add(essay(spec, "Essay question " + seq.incrementAndGet()));
        return new ParseResult(list, List.of());
    }

    private void llm(Function<ChunkSpec, ParseResult> behaviour) {
        when(chunkGenerator.generate(any(), any(), any())).thenAnswer(inv -> behaviour.apply(inv.getArgument(0)));
    }

    private void llmAlwaysSucceeds() {
        llm(s -> fresh(s, s.mcqTarget(), s.essayTarget()));
    }

    private long run(QuizSet set, int attempts) {
        GenerationJob job = newJob(set.getId(), JobStatus.RUNNING, attempts, Instant.now());
        processor.process(new ClaimedJob(job.getId(), set.getId(), attempts, 3));
        return job.getId();
    }

    private void rerun(long jobId, QuizSet set, int attempts) {
        jdbc.update("UPDATE generation_jobs SET status = 'RUNNING', attempts = ? WHERE id = ?", attempts, jobId);
        processor.process(new ClaimedJob(jobId, set.getId(), attempts, 3));
    }

    private List<Integer> orderIndexes(UUID quizSetId) {
        return jdbc.queryForList(
                "SELECT order_index FROM questions WHERE quiz_set_id = ? ORDER BY order_index", Integer.class, quizSetId);
    }

    private List<String> contents(UUID quizSetId) {
        return jdbc.queryForList(
                "SELECT content FROM questions WHERE quiz_set_id = ? ORDER BY order_index", String.class, quizSetId);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    // =====================================================================
    @Nested
    class Pipeline {

        // P1: mọi đoạn thành công → READY, đánh số 1..n liên tục, đúng thứ tự trang, MCQ có 4 đáp án / 1 đúng
        @Test
        void allChunksSucceed_ready() {
            llmAlwaysSucceeds();
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            long jobId = run(set, 1);

            QuizSet after = reload(set.getId());
            assertThat(after.getStatus()).isEqualTo(QuizSetStatus.READY);
            assertThat(after.getGeneratedCount()).isEqualTo(30);
            assertThat(after.getTotalChunks()).isEqualTo(5);
            assertThat(after.getCompletedChunks()).isEqualTo(5);
            assertThat(after.getErrorMessage()).isNull();
            assertThat(jobStatus(jobId)).isEqualTo("DONE");

            assertThat(orderIndexes(set.getId()))
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 30).boxed().toList());
            assertThat(jdbc.queryForList(
                    "SELECT source_pages[1] FROM questions WHERE quiz_set_id = ? ORDER BY order_index",
                    Integer.class, set.getId()))
                    .isSorted();                                           // câu đi theo thứ tự trang
            assertThat(count("""
                    SELECT count(*) FROM questions q WHERE q.quiz_set_id = ?
                      AND (SELECT count(*) FROM question_options o WHERE o.question_id = q.id) = 4
                      AND (SELECT count(*) FROM question_options o WHERE o.question_id = q.id AND o.is_correct) = 1
                    """, set.getId())).isEqualTo(30);

            assertThat(chunkRepository.findByQuizSetIdOrderByChunkIndex(set.getId()))
                    .hasSize(5).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(ChunkStatus.DONE));
            verify(chunkGenerator, times(5)).generate(any(), any(), any());   // đủ câu → không gọi bù
        }

        // P2: các đoạn trả cùng một bộ câu → chỉ lưu 1 lần; gọi bù cũng trùng → PARTIAL
        @Test
        void duplicatesAcrossChunks_savedOnce() {
            llm(s -> new ParseResult(
                    java.util.stream.IntStream.range(0, s.mcqTarget()).<GeneratedQuestion>mapToObj(i -> mcq(s, "Shared " + i)).toList(),
                    List.of()));
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            long jobId = run(set, 1);

            QuizSet after = reload(set.getId());
            assertThat(after.getStatus()).isEqualTo(QuizSetStatus.PARTIAL);
            assertThat(after.getGeneratedCount()).isEqualTo(6);
            assertThat(contents(set.getId())).doesNotHaveDuplicates();
            assertThat(jobStatus(jobId)).isEqualTo("DONE");
        }

        // P3: 1 đoạn lỗi cả 2 lần → FAILED, các đoạn khác gọi bù cho đủ → READY
        @Test
        void oneChunkFails_otherChunksFillTheGap() {
            AtomicInteger badChunkCalls = new AtomicInteger();
            llm(s -> {
                if (!isTopUp(s) && chunkIndexOf(s) == 2) {
                    badChunkCalls.incrementAndGet();
                    throw new LlmUnavailableException("provider down");
                }
                return fresh(s, s.mcqTarget(), s.essayTarget());
            });
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            run(set, 1);

            assertThat(badChunkCalls).hasValue(2);                           // chunk-max-attempts = 2
            GenerationChunk bad = chunkRepository.findByQuizSetIdOrderByChunkIndex(set.getId()).get(2);
            assertThat(bad.getStatus()).isEqualTo(ChunkStatus.FAILED);
            assertThat(bad.getAttempts()).isEqualTo(2);
            assertThat(bad.getLastError()).contains("provider down");

            QuizSet after = reload(set.getId());
            assertThat(after.getStatus()).isEqualTo(QuizSetStatus.READY);
            assertThat(after.getGeneratedCount()).isEqualTo(30);
            assertThat(after.getCompletedChunks()).isEqualTo(5);             // tiến độ đếm cả đoạn lỗi
        }

        // P4: LLM trả JSON không có câu hợp lệ → thử lại trong cùng job, lần 2 thành công
        @Test
        void emptyResult_retriedWithinJob() {
            Map<Integer, AtomicInteger> calls = new ConcurrentHashMap<>();
            llm(s -> calls.computeIfAbsent(chunkIndexOf(s), k -> new AtomicInteger()).incrementAndGet() == 1
                    ? new ParseResult(List.of(), List.of("#1: answer must be A, B, C or D"))
                    : fresh(s, s.mcqTarget(), 0));
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            run(set, 1);

            assertThat(reload(set.getId()).getStatus()).isEqualTo(QuizSetStatus.READY);
            assertThat(chunkRepository.findByQuizSetIdOrderByChunkIndex(set.getId()))
                    .allSatisfy(c -> assertThat(c.getAttempts()).isEqualTo(2));
        }

        // P5: tự luận: lưu rubric dạng jsonb, đáp án mẫu, không có lựa chọn A-D
        @Test
        void essays_persistedWithRubricAndNoOptions() {
            llmAlwaysSucceeds();
            QuizSet set = newQuizSet(userId, documentId, QuestionType.ESSAY, 3);

            run(set, 1);

            assertThat(reload(set.getId()).getStatus()).isEqualTo(QuizSetStatus.READY);
            assertThat(count("""
                    SELECT count(*) FROM questions WHERE quiz_set_id = ? AND type = 'ESSAY'
                      AND model_answer = 'Model answer' AND rubric = '["point 1", "point 2"]'::jsonb
                    """, set.getId())).isEqualTo(3);
            assertThat(count("""
                    SELECT count(*) FROM question_options o JOIN questions q ON q.id = o.question_id
                    WHERE q.quiz_set_id = ?""", set.getId())).isZero();
        }
    }

    // =====================================================================
    @Nested
    class FailureAndRetry {

        // F1: mọi đoạn lỗi, còn lượt → job QUEUED chờ retry, quiz set về QUEUED
        @Test
        void allChunksFail_attemptsLeft_requeued() {
            llm(s -> { throw new LlmUnavailableException("all providers down"); });
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            long jobId = run(set, 1);

            assertThat(jobStatus(jobId)).isEqualTo("QUEUED");
            Double wait = jdbc.queryForObject(
                    "SELECT extract(epoch FROM next_run_at - now()) FROM generation_jobs WHERE id = ?", Double.class, jobId);
            assertThat(wait).isPositive();
            QuizSet after = reload(set.getId());
            assertThat(after.getStatus()).isEqualTo(QuizSetStatus.QUEUED);
            assertThat(after.getErrorMessage()).isNull();
        }

        // F2: lần thử cuối cũng lỗi → FAILED kèm thông báo dễ hiểu (không lộ lỗi kỹ thuật)
        @Test
        void allChunksFail_lastAttempt_failed() {
            llm(s -> { throw new LlmUnavailableException("HTTP 402: Insufficient Balance"); });
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            long jobId = run(set, 3);

            assertThat(jobStatus(jobId)).isEqualTo("FAILED");
            QuizSet after = reload(set.getId());
            assertThat(after.getStatus()).isEqualTo(QuizSetStatus.FAILED);
            assertThat(after.getErrorMessage())
                    .isEqualTo(GenerationJobProcessor.USER_FAILURE_MESSAGE)
                    .doesNotContain("402");
        }

        // F3: job bị claim lại quá số lần (do app sập liên tục) → FAILED ngay, không gọi LLM
        @Test
        void tooManyAttempts_failedWithoutCallingLlm() {
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            long jobId = run(set, 4);

            assertThat(jobStatus(jobId)).isEqualTo("FAILED");
            assertThat(reload(set.getId()).getStatus()).isEqualTo(QuizSetStatus.FAILED);
            verifyNoInteractions(chunkGenerator);
        }

        // F4: user đã xoá bộ đề trong lúc chờ → bỏ qua job, không gọi LLM
        @Test
        void deletedQuizSet_skipped() {
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);
            jdbc.update("UPDATE quiz_sets SET deleted_at = now() WHERE id = ?", set.getId());

            long jobId = run(set, 1);

            assertThat(jobStatus(jobId)).isEqualTo("DONE");
            verifyNoInteractions(chunkGenerator);
        }

        // F5: lần chạy đầu sập giữa chừng sau khi xong đoạn 0, 1 → lần sau CHỈ chạy đoạn 2, 3, 4
        @Test
        void retryAfterCrash_resumesOnlyUnfinishedChunks() {
            final class SimulatedCrash extends Error {
                SimulatedCrash() { super("simulated crash"); }
            }
            llm(s -> {
                if (chunkIndexOf(s) >= 2) throw new SimulatedCrash();       // Error: runChunk không bắt được → job lỗi
                return fresh(s, s.mcqTarget(), 0);
            });
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);
            long jobId = run(set, 1);

            assertThat(jobStatus(jobId)).isEqualTo("QUEUED");
            assertThat(questionRepository.countByQuizSetId(set.getId())).isEqualTo(12);

            // Lần chạy thứ 2: LLM bình thường; ghi lại đoạn nào được gọi
            List<Integer> calledChunks = new CopyOnWriteArrayList<>();
            reset(chunkGenerator);
            llm(s -> {
                calledChunks.add(chunkIndexOf(s));
                return fresh(s, s.mcqTarget(), 0);
            });
            rerun(jobId, set, 2);

            assertThat(calledChunks).containsExactlyInAnyOrder(2, 3, 4);
            QuizSet after = reload(set.getId());
            assertThat(after.getStatus()).isEqualTo(QuizSetStatus.READY);
            assertThat(after.getGeneratedCount()).isEqualTo(30);
            assertThat(after.getCompletedChunks()).isEqualTo(5);
            assertThat(chunkRepository.findByQuizSetIdOrderByChunkIndex(set.getId())).hasSize(5);   // không tạo trùng đoạn
        }
    }

    // =====================================================================
    @Nested
    class TopUp {

        // T1: mỗi đoạn thiếu 1 câu → vòng bù đầu xin đúng 5 câu (1 câu/đoạn) → READY
        @Test
        void shortByOnePerChunk_toppedUpToExactCount() {
            List<ChunkSpec> topUps = new CopyOnWriteArrayList<>();
            llm(s -> {
                if (isTopUp(s)) {
                    topUps.add(s);
                    return fresh(s, s.mcqTarget(), 0);
                }
                return fresh(s, s.mcqTarget() - 1, 0);
            });
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            run(set, 1);

            assertThat(reload(set.getId()).getStatus()).isEqualTo(QuizSetStatus.READY);
            assertThat(questionRepository.countByQuizSetId(set.getId())).isEqualTo(30);
            assertThat(topUps).hasSize(5).allSatisfy(s -> {
                assertThat(s.mcqTarget()).isEqualTo(1);
                assertThat(s.essayTarget()).isZero();
                assertThat(s.avoidQuestions()).hasSize(5);                    // gửi kèm các câu đã có của đoạn đó
            });
        }

        // T2: gọi bù trả thừa câu → không bao giờ vượt số câu yêu cầu
        @Test
        void topUpReturnsTooMany_neverExceedsRequested() {
            llm(s -> isTopUp(s) ? fresh(s, s.mcqTarget() + 10, 0) : fresh(s, s.mcqTarget() - 1, 0));
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            run(set, 1);

            assertThat(questionRepository.countByQuizSetId(set.getId())).isEqualTo(30);
            assertThat(reload(set.getId()).getGeneratedCount()).isEqualTo(30);
        }

        // T3: gọi bù lỗi cả 2 vòng → vẫn dùng được: PARTIAL, job DONE
        @Test
        void topUpAlwaysFails_partial() {
            AtomicInteger topUpCalls = new AtomicInteger();
            llm(s -> {
                if (isTopUp(s)) {
                    topUpCalls.incrementAndGet();
                    throw new InvalidLlmOutputException("Response is not valid JSON");
                }
                return fresh(s, s.mcqTarget() - 1, 0);
            });
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            long jobId = run(set, 1);

            QuizSet after = reload(set.getId());
            assertThat(after.getStatus()).isEqualTo(QuizSetStatus.PARTIAL);
            assertThat(after.getGeneratedCount()).isEqualTo(25);
            assertThat(jobStatus(jobId)).isEqualTo("DONE");
            assertThat(topUpCalls).hasValue(10);                             // 2 vòng × 5 đoạn
        }

        // T4: MIXED thiếu đúng phần tự luận → chỉ xin tự luận, không xin thêm trắc nghiệm
        @Test
        void mixedMissingEssays_asksOnlyForEssays() {
            List<ChunkSpec> topUps = new CopyOnWriteArrayList<>();
            llm(s -> {
                if (isTopUp(s)) {
                    topUps.add(s);
                    return fresh(s, s.mcqTarget(), s.essayTarget());
                }
                return fresh(s, s.mcqTarget(), 0);                           // lời gọi chính "quên" tự luận
            });
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MIXED, 30);   // 27 MCQ + 3 essay

            run(set, 1);

            assertThat(topUps).allSatisfy(s -> assertThat(s.mcqTarget()).isZero());
            assertThat(topUps.stream().mapToInt(ChunkSpec::essayTarget).sum()).isEqualTo(3);
            assertThat(questionRepository.countByQuizSetIdAndType(set.getId(), QuestionType.MCQ)).isEqualTo(27);
            assertThat(questionRepository.countByQuizSetIdAndType(set.getId(), QuestionType.ESSAY)).isEqualTo(3);
            assertThat(reload(set.getId()).getStatus()).isEqualTo(QuizSetStatus.READY);
        }

        // T5: mọi đoạn đều lỗi → không có đoạn nào để gọi bù → không gọi thêm
        @Test
        void noSuccessfulChunk_noTopUpCalls() {
            AtomicInteger topUpCalls = new AtomicInteger();
            llm(s -> {
                if (isTopUp(s)) topUpCalls.incrementAndGet();
                throw new LlmUnavailableException("down");
            });
            QuizSet set = newQuizSet(userId, documentId, QuestionType.MCQ, 30);

            run(set, 1);

            assertThat(topUpCalls).hasValue(0);
        }
    }

    // =====================================================================
    @Nested
    class Cache {

        private static final String KEY = "c".repeat(64);

        /** Bộ đề nguồn đã READY với cache_key KEY. */
        private QuizSet readySource() {
            llmAlwaysSucceeds();
            QuizSet source = newQuizSet(userId, documentId, QuestionType.MCQ, 30, KEY);
            run(source, 1);
            assertThat(reload(source.getId()).getStatus()).isEqualTo(QuizSetStatus.READY);
            clearInvocations(chunkGenerator);
            return source;
        }

        // K1: trùng cache_key với bộ READY → copy, KHÔNG gọi LLM
        @Test
        void sameKeyAsReadySet_copiedWithoutLlm() {
            QuizSet source = readySource();
            QuizSet target = newQuizSet(userId, documentId, QuestionType.MCQ, 30, KEY);

            long jobId = run(target, 1);

            verifyNoInteractions(chunkGenerator);
            assertThat(jobStatus(jobId)).isEqualTo("DONE");
            QuizSet after = reload(target.getId());
            assertThat(after.getStatus()).isEqualTo(QuizSetStatus.READY);
            assertThat(after.getGeneratedCount()).isEqualTo(30);

            assertThat(contents(target.getId())).containsExactlyElementsOf(contents(source.getId()));
            assertThat(count("SELECT count(*) FROM questions WHERE quiz_set_id = ? AND chunk_id IS NULL", target.getId()))
                    .isEqualTo(30);
            assertThat(count("""
                    SELECT count(*) FROM question_options o JOIN questions q ON q.id = o.question_id
                    WHERE q.quiz_set_id = ? AND o.is_correct AND o.label = 'B'""", target.getId()))
                    .isEqualTo(30);                                           // đáp án đúng được copy đúng
        }

        // K2: bộ nguồn chỉ PARTIAL → không dùng cache
        @Test
        void partialSource_notUsed() {
            QuizSet source = readySource();
            jdbc.update("UPDATE quiz_sets SET status = 'PARTIAL' WHERE id = ?", source.getId());
            QuizSet target = newQuizSet(userId, documentId, QuestionType.MCQ, 30, KEY);

            run(target, 1);

            verify(chunkGenerator, atLeastOnce()).generate(any(), any(), any());
        }

        // K3: bộ nguồn đã bị xoá → không dùng cache
        @Test
        void deletedSource_notUsed() {
            QuizSet source = readySource();
            jdbc.update("UPDATE quiz_sets SET deleted_at = now() WHERE id = ?", source.getId());
            QuizSet target = newQuizSet(userId, documentId, QuestionType.MCQ, 30, KEY);

            run(target, 1);

            verify(chunkGenerator, atLeastOnce()).generate(any(), any(), any());
        }

        // K4: bộ đích đã có câu (đang chạy dở) → không copy chồng lên
        @Test
        void targetAlreadyHasQuestions_notOverwritten() {
            readySource();
            QuizSet target = newQuizSet(userId, documentId, QuestionType.MCQ, 30, KEY);
            jdbc.update("""
                    INSERT INTO questions (quiz_set_id, type, order_index, content, source_pages, normalized_hash)
                    VALUES (?, 'MCQ', 1, 'Existing question', '{1}', ?)""", target.getId(), "e".repeat(64));

            run(target, 1);

            verify(chunkGenerator, atLeastOnce()).generate(any(), any(), any());
            assertThat(contents(target.getId())).contains("Existing question").doesNotHaveDuplicates();
        }
    }
}
