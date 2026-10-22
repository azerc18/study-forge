package com.az.studyforge.generation.service;

import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.*;
import com.az.studyforge.generation.llm.model.GeneratedEssay;
import com.az.studyforge.generation.llm.model.GeneratedMcq;
import com.az.studyforge.generation.llm.model.GeneratedQuestion;
import com.az.studyforge.generation.planning.QuestionMix;
import com.az.studyforge.generation.repository.GenerationChunkRepository;
import com.az.studyforge.generation.repository.QuestionRepository;
import com.az.studyforge.generation.repository.QuizSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

@Component
@RequiredArgsConstructor
public class QuestionWriter {

    private static final String[] LABELS = {"A", "B", "C", "D"};

    private final QuestionRepository questionRepository;
    private final GenerationChunkRepository chunkRepository;
    private final QuizSetRepository quizSetRepository;
    private final JsonMapper json;

    @Transactional
    public int saveChunk(UUID quizSetId, GenerationChunk chunk, List<GeneratedQuestion> questions, int attempts) {
        int saved = insert(quizSetId, chunk, questions, Integer.MAX_VALUE, Integer.MAX_VALUE);

        GenerationChunk c = chunkRepository.findById(chunk.getId()).orElseThrow();
        c.setStatus(ChunkStatus.DONE);
        c.setAttempts(attempts);
        c.setLastError(null);

        QuizSet set = quizSetRepository.findById(quizSetId).orElseThrow();
        set.setCompletedChunks(set.getCompletedChunks() + 1);
        set.setGeneratedCount(set.getGeneratedCount() + saved);
        return saved;
    }

    @Transactional
    public int saveTopUp(UUID quizSetId, GenerationChunk donor, List<GeneratedQuestion> questions, QuestionMix target) {
        int mcqRoom = target.mcq() - (int) questionRepository.countByQuizSetIdAndType(quizSetId, QuestionType.MCQ);
        int essayRoom = target.essay() - (int) questionRepository.countByQuizSetIdAndType(quizSetId, QuestionType.ESSAY);
        int saved = insert(quizSetId, donor, questions, mcqRoom, essayRoom);

        QuizSet set = quizSetRepository.findById(quizSetId).orElseThrow();
        set.setGeneratedCount(set.getGeneratedCount() + saved);
        return saved;
    }

    @Transactional
    public void markChunkFailed(UUID quizSetId, GenerationChunk chunk, int attempts, String error) {
        GenerationChunk c = chunkRepository.findById(chunk.getId()).orElseThrow();
        c.setStatus(ChunkStatus.FAILED);
        c.setAttempts(attempts);
        c.setLastError(error == null ? null : error.length() <= 1000 ? error : error.substring(0, 1000));

        QuizSet set = quizSetRepository.findById(quizSetId).orElseThrow();
        set.setCompletedChunks(set.getCompletedChunks() + 1);
    }

    @Transactional
    public int copyFromCache(UUID sourceQuizSetId, UUID targetQuizSetId) {
        List<Question> source = questionRepository.findWithOptionsByQuizSetId(sourceQuizSetId);

        for (Question s : source) {
            Question copy = Question.builder()
                    .quizSetId(targetQuizSetId)
                    .type(s.getType())
                    .orderIndex(s.getOrderIndex())
                    .content(s.getContent())
                    .explanation(s.getExplanation())
                    .modelAnswer(s.getModelAnswer())
                    .rubric(s.getRubric())
                    .sourcePages(new ArrayList<>(s.getSourcePages()))
                    .normalizedHash(s.getNormalizedHash())
                    .topic(s.getTopic())
                    .build();
            for (QuestionOption o : s.getOptions()) {
                copy.addOption(QuestionOption.builder()
                        .label(o.getLabel()).content(o.getContent()).correct(o.isCorrect()).build());
            }
            questionRepository.save(copy);
        }

        QuizSet set = quizSetRepository.findById(targetQuizSetId).orElseThrow();
        set.setStatus(QuizSetStatus.READY);
        set.setGeneratedCount(source.size());
        set.setErrorMessage(null);
        return source.size();
    }

    private int insert(UUID quizSetId, GenerationChunk chunk, List<GeneratedQuestion> questions,
                       int mcqRoom, int essayRoom) {
        Set<String> existing = new HashSet<>(questionRepository.findHashesByQuizSetId(quizSetId));
        int order = chunk.getChunkIndex() * 1000 + (int) questionRepository.countByChunkId(chunk.getId());

        int saved = 0;
        for (GeneratedQuestion g : questions) {
            boolean mcq = g instanceof GeneratedMcq;
            if (mcq ? mcqRoom <= 0 : essayRoom <= 0) continue;
            if (!existing.add(g.normalizedHash())) continue;

            questionRepository.save(toEntity(g, quizSetId, chunk.getId(), order + saved));
            saved++;
            if (mcq) mcqRoom--; else essayRoom--;
        }
        return saved;
    }

    private Question toEntity(GeneratedQuestion g, UUID quizSetId, Long chunkId, int order) {
        Question q = Question.builder()
                .quizSetId(quizSetId).chunkId(chunkId).orderIndex(order)
                .content(g.content()).sourcePages(g.sourcePages()).normalizedHash(g.normalizedHash())
                .topic(g.topic())
                .build();

        switch (g) {
            case GeneratedMcq m -> {
                q.setType(QuestionType.MCQ);
                q.setExplanation(m.explanation());
                for (int i = 0; i < 4; i++) {
                    q.addOption(QuestionOption.builder()
                            .label(LABELS[i]).content(m.options().get(i)).correct(i == m.correctIndex()).build());
                }
            }
            case GeneratedEssay e -> {
                q.setType(QuestionType.ESSAY);
                q.setModelAnswer(e.modelAnswer());
                q.setRubric(json.writeValueAsString(e.rubric()));
            }
        }
        return q;
    }
}
