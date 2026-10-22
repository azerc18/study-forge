package com.az.studyforge.generation.service;

import com.az.studyforge.document.service.DocumentService;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.*;
import com.az.studyforge.generation.llm.exception.LlmUnavailableException;
import com.az.studyforge.generation.llm.model.ChunkSpec;
import com.az.studyforge.generation.llm.model.PageText;
import com.az.studyforge.generation.llm.model.ParseResult;
import com.az.studyforge.generation.llm.parse.InvalidLlmOutputException;
import com.az.studyforge.generation.planning.Allocation;
import com.az.studyforge.generation.planning.ChunkPlanner;
import com.az.studyforge.generation.planning.PlannedChunk;
import com.az.studyforge.generation.planning.QuestionMix;
import com.az.studyforge.generation.repository.GenerationChunkRepository;
import com.az.studyforge.generation.repository.QuestionRepository;
import com.az.studyforge.generation.repository.QuizSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationJobProcessor {

    static final String USER_FAILURE_MESSAGE = "We couldn't generate questions right now. Please try again later.";
    private static final int MAX_PER_CALL = 15;
    private static final int MAX_PREVIOUS = 30;

    private final JobQueue queue;
    private final QuizSetRepository quizSetRepository;
    private final GenerationChunkRepository chunkRepository;
    private final QuestionRepository questionRepository;
    private final DocumentService documentService;
    private final ChunkPlanner planner;
    private final ChunkGenerator chunkGenerator;
    private final QuestionWriter writer;
    private final TransactionTemplate tx;
    private final ThreadPoolTaskExecutor llmCallExecutor;
    private final GenerationProperties props;

    public void process(ClaimedJob job) {
        try {
            if (job.attempts() > job.maxAttempts()) {
                throw new IllegalStateException("Gave up after " + job.maxAttempts() + " attempts");
            }
            QuizSet set = quizSetRepository.findById(job.quizSetId()).orElse(null);
            if (set == null || set.getDeletedAt() != null) {
                queue.complete(job.id());
                return;
            }
            if (copyFromCacheIfAvailable(job, set)) return;              // ⓪

            List<PlannedChunk> plan = prepare(set);                      // ①
            Object writeLock = new Object();
            runChunks(job, set, plan, writeLock);                        // ②
            topUp(job, set, plan, writeLock);                            // ③
            finish(job, set.getId());                                    // ④
        } catch (Exception e) {
            log.error("Generation job {} failed (attempt {}/{})", job.id(), job.attempts(), job.maxAttempts(), e);
            handleFailure(job, e);
        }
    }

    private List<PlannedChunk> prepare(QuizSet set) {
        List<PageText> pages = documentService.getPagesForGeneration(set.getDocumentId()).stream()
                .map(p -> new PageText(p.pageNumber(), p.content()))
                .toList();
        List<PlannedChunk> plan = planner.plan(pages, QuestionMix.of(set.getQuestionType(), set.getRequestedCount()));

        tx.executeWithoutResult(s -> {
            List<GenerationChunk> existing = chunkRepository.findByQuizSetIdOrderByChunkIndex(set.getId());
            if (existing.isEmpty()) {
                chunkRepository.saveAll(plan.stream().map(c -> GenerationChunk.builder()
                        .quizSetId(set.getId()).chunkIndex(c.index())
                        .pageFrom(c.pageFrom()).pageTo(c.pageTo()).wordCount(c.wordCount())
                        .mcqTarget(c.mcqTarget()).essayTarget(c.essayTarget())
                        .build()).toList());
            } else if (existing.size() != plan.size()) {
                throw new IllegalStateException("Chunk plan changed between runs");
            } else {
                existing.stream().filter(c -> c.getStatus() != ChunkStatus.DONE)
                        .forEach(c -> c.setStatus(ChunkStatus.PENDING));
            }

            QuizSet s2 = quizSetRepository.findById(set.getId()).orElseThrow();
            s2.setStatus(QuizSetStatus.GENERATING);
            s2.setTotalChunks(plan.size());
            s2.setCompletedChunks((int) existing.stream().filter(c -> c.getStatus() == ChunkStatus.DONE).count());
            s2.setGeneratedCount((int) questionRepository.countByQuizSetId(set.getId()));
            s2.setErrorMessage(null);
        });
        return plan;
    }

    private void runChunks(ClaimedJob job, QuizSet set, List<PlannedChunk> plan, Object writeLock) {
        List<CompletableFuture<Void>> futures = chunkRepository.findByQuizSetIdOrderByChunkIndex(set.getId()).stream()
                .filter(c -> c.getStatus() != ChunkStatus.DONE)
                .map(c -> CompletableFuture.runAsync(() -> {
                    runChunk(set, c, plan.get(c.getChunkIndex()), writeLock);
                    queue.heartbeat(job.id());
                }, llmCallExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void runChunk(QuizSet set, GenerationChunk chunk, PlannedChunk planned, Object writeLock) {
        ChunkSpec spec = new ChunkSpec(planned.pageFrom(), planned.pageTo(), planned.pages(),
                planned.mcqTarget(), planned.essayTarget(), set.getLanguage(), previousQuestions(set, planned));

        String lastError = "Unknown error";
        for (int attempt = 1; attempt <= props.chunkMaxAttempts(); attempt++) {
            try {
                ParseResult result = chunkGenerator.generate(spec, set.getId(), chunk.getId());
                if (!result.rejected().isEmpty()) {
                    log.info("Chunk {}: rejected {}", chunk.getChunkIndex(), result.rejected());
                }
                if (result.questions().isEmpty()) throw new InvalidLlmOutputException("No valid questions");

                synchronized (writeLock) {
                    writer.saveChunk(set.getId(), chunk, result.questions(), attempt);
                }
                return;
            } catch (InvalidLlmOutputException | LlmUnavailableException e) {
                lastError = e.getMessage();
                log.warn("Chunk {} attempt {} failed: {}", chunk.getChunkIndex(), attempt, lastError);
            } catch (RuntimeException e) {
                lastError = e.toString();
                log.error("Chunk {} crashed", chunk.getChunkIndex(), e);
                break;
            }
        }
        synchronized (writeLock) {
            writer.markChunkFailed(set.getId(), chunk, props.chunkMaxAttempts(), lastError);
        }
    }

    private void finish(ClaimedJob job, UUID quizSetId) {
        Integer generated = tx.execute(s -> {
            int count = (int) questionRepository.countByQuizSetId(quizSetId);
            if (count == 0) return 0;

            questionRepository.renumber(quizSetId);
            QuizSet set = quizSetRepository.findById(quizSetId).orElseThrow();
            set.setGeneratedCount(count);
            set.setStatus(count >= set.getRequestedCount() ? QuizSetStatus.READY : QuizSetStatus.PARTIAL);
            return count;
        });
        if (generated == null || generated == 0) throw new IllegalStateException("No questions were generated");

        queue.complete(job.id());
        log.info("Job {} done: {} questions", job.id(), generated);
    }

    private void handleFailure(ClaimedJob job, Exception e) {
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        boolean willRetry = queue.failOrRetry(job, reason);

        tx.executeWithoutResult(s -> quizSetRepository.findById(job.quizSetId()).ifPresent(set -> {
            set.setStatus(willRetry ? QuizSetStatus.QUEUED : QuizSetStatus.FAILED);
            set.setErrorMessage(willRetry ? null : USER_FAILURE_MESSAGE);
        }));
    }

    private boolean copyFromCacheIfAvailable(ClaimedJob job, QuizSet set) {
        if (!set.isUseCache()) return false;                                       // E4: "tạo bộ khác" → luôn gọi LLM
        if (questionRepository.countByQuizSetId(set.getId()) > 0) return false;   // đang chạy dở: không ghi đè

        Optional<QuizSet> source = quizSetRepository
                .findFirstByCacheKeyAndStatusAndDeletedAtIsNullAndIdNotOrderByCreatedAtAsc(
                        set.getCacheKey(), QuizSetStatus.READY, set.getId());
        if (source.isEmpty()) return false;

        int copied = writer.copyFromCache(source.get().getId(), set.getId());
        queue.complete(job.id());
        log.info("Job {}: copied {} questions from cache (source {})", job.id(), copied, source.get().getId());
        return true;
    }

    private void topUp(ClaimedJob job, QuizSet set, List<PlannedChunk> plan, Object writeLock) {
        QuestionMix target = QuestionMix.of(set.getQuestionType(), set.getRequestedCount());

        for (int round = 1; round <= props.topUpRounds(); round++) {
            int mcqNeed = Math.max(0, target.mcq()
                    - (int) questionRepository.countByQuizSetIdAndType(set.getId(), QuestionType.MCQ));
            int essayNeed = Math.max(0, target.essay()
                    - (int) questionRepository.countByQuizSetIdAndType(set.getId(), QuestionType.ESSAY));
            if (mcqNeed + essayNeed == 0) return;

            List<GenerationChunk> donors = chunkRepository.findByQuizSetIdOrderByChunkIndex(set.getId()).stream()
                    .filter(c -> c.getStatus() == ChunkStatus.DONE)
                    .toList();
            if (donors.isEmpty()) return;                                   // không đoạn nào thành công → để finish xử lý

            int[] weights = donors.stream().mapToInt(GenerationChunk::getWordCount).toArray();
            int[] mcq = Allocation.proportional(mcqNeed, weights);
            int[] essay = Allocation.proportional(essayNeed, weights);
            log.info("Job {} top-up round {}: need {} MCQ + {} essay from {} chunk(s)",
                    job.id(), round, mcqNeed, essayNeed, donors.size());

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < donors.size(); i++) {
                int m = Math.min(mcq[i], MAX_PER_CALL);
                int e = Math.min(essay[i], MAX_PER_CALL - m);
                if (m + e == 0) continue;

                GenerationChunk donor = donors.get(i);
                PlannedChunk planned = plan.get(donor.getChunkIndex());
                futures.add(CompletableFuture.runAsync(() -> {
                    topUpChunk(set, donor, planned, m, e, target, writeLock);
                    queue.heartbeat(job.id());
                }, llmCallExecutor));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
    }

    private void topUpChunk(QuizSet set, GenerationChunk donor, PlannedChunk planned,
                            int mcq, int essay, QuestionMix target, Object writeLock) {
        List<String> avoid = new ArrayList<>(questionRepository.findContentsByChunkId(donor.getId()));
        avoid.addAll(previousQuestions(set, planned));                 // câu trong bộ này trước, rồi tới bộ cũ
        ChunkSpec spec = new ChunkSpec(planned.pageFrom(), planned.pageTo(), planned.pages(),
                mcq, essay, set.getLanguage(), avoid);
        try {
            ParseResult result = chunkGenerator.generate(spec, set.getId(), donor.getId());
            synchronized (writeLock) {
                int saved = writer.saveTopUp(set.getId(), donor, result.questions(), target);
                log.info("Chunk {} top-up: asked {}, saved {}", donor.getChunkIndex(), mcq + essay, saved);
            }
        } catch (InvalidLlmOutputException | LlmUnavailableException ex) {
            log.warn("Chunk {} top-up failed: {}", donor.getChunkIndex(), ex.getMessage());
        }
    }

    /** E4: câu ở các bộ cũ (cùng user, cùng tài liệu, cùng khoảng trang) để LLM tránh. Bộ thường → rỗng. */
    private List<String> previousQuestions(QuizSet set, PlannedChunk planned) {
        if (set.isUseCache()) return List.of();
        List<String> types = switch (set.getQuestionType()) {
            case MCQ -> List.of("MCQ");
            case ESSAY -> List.of("ESSAY");
            case MIXED -> List.of("MCQ", "ESSAY");
        };
        return questionRepository.findPreviousContents(set.getUserId(), set.getDocumentId(), set.getId(),
                planned.pageFrom(), planned.pageTo(), types, MAX_PREVIOUS);
    }
}
