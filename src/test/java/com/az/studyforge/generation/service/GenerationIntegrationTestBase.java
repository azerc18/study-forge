package com.az.studyforge.generation.service;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.DocumentPage;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.repository.DocumentPageRepository;
import com.az.studyforge.document.repository.DocumentRepository;
import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.GenerationJob;
import com.az.studyforge.generation.entity.JobStatus;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.repository.GenerationChunkRepository;
import com.az.studyforge.generation.repository.GenerationJobRepository;
import com.az.studyforge.generation.repository.QuestionRepository;
import com.az.studyforge.generation.repository.QuizSetRepository;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
abstract class GenerationIntegrationTestBase {

    @MockitoBean protected ChunkGenerator chunkGenerator;

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected DocumentRepository documentRepository;
    @Autowired protected DocumentPageRepository pageRepository;
    @Autowired protected QuizSetRepository quizSetRepository;
    @Autowired protected GenerationJobRepository jobRepository;
    @Autowired protected GenerationChunkRepository chunkRepository;
    @Autowired protected QuestionRepository questionRepository;

    private final List<UUID> createdUsers = new ArrayList<>();

    @AfterEach
    void deleteCreatedUsers() {
        createdUsers.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        createdUsers.clear();
    }

    protected UUID newUser() {
        String sub = "sub-" + UUID.randomUUID();
        UUID id = userRepository.save(User.builder().googleSub(sub).email(sub + "@test.com").dailyQuota(100).build())
                .getId();
        createdUsers.add(id);
        return id;
    }

    protected UUID newDocument(UUID userId, int pages, int wordsPerPage) {
        UUID docId = documentRepository.save(Document.builder()
                .userId(userId)
                .originalFilename("lecture.pdf")
                .fileType(FileType.PDF)
                .fileSizeBytes(1000)
                .fileHash("a".repeat(64))
                .storageKey("2026/10/" + UUID.randomUUID() + ".pdf")
                .status(DocumentStatus.READY)
                .pageCount(pages)
                .wordCount(pages * wordsPerPage)
                .build()).getId();

        List<DocumentPage> rows = new ArrayList<>();
        for (int p = 1; p <= pages; p++) {
            StringBuilder sb = new StringBuilder();
            for (int w = 0; w < wordsPerPage; w++) {
                sb.append('p').append(p).append('w').append(w).append((w + 1) % 10 == 0 ? '\n' : ' ');
            }
            rows.add(DocumentPage.builder()
                    .documentId(docId).pageNumber(p).content(sb.toString().strip()).wordCount(wordsPerPage)
                    .build());
        }
        pageRepository.saveAll(rows);
        return docId;
    }

    protected QuizSet newQuizSet(UUID userId, UUID documentId, QuestionType type, int count) {
        return newQuizSet(userId, documentId, type, count, HashUtils.sha256Hex(UUID.randomUUID().toString()));
    }

    protected QuizSet newQuizSet(UUID userId, UUID documentId, QuestionType type, int count, String cacheKey) {
        return quizSetRepository.save(QuizSet.builder()
                .userId(userId).documentId(documentId).title("Test set")
                .questionType(type).requestedCount(count).language(OutputLanguage.VI)
                .cacheKey(cacheKey).promptVersion("test")
                .build());
    }

    protected GenerationJob newJob(UUID quizSetId, JobStatus status, int attempts, Instant nextRunAt) {
        return jobRepository.save(GenerationJob.builder()
                .quizSetId(quizSetId).status(status).attempts(attempts).nextRunAt(nextRunAt)
                .lockedBy(status == JobStatus.RUNNING ? "test-worker" : null)
                .lockedAt(status == JobStatus.RUNNING ? Instant.now() : null)
                .build());
    }

    protected String jobStatus(long jobId) {
        return jdbc.queryForObject("SELECT status FROM generation_jobs WHERE id = ?", String.class, jobId);
    }

    protected QuizSet reload(UUID quizSetId) {
        return quizSetRepository.findById(quizSetId).orElseThrow();
    }
}
