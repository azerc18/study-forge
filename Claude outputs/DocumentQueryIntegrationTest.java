package com.az.studyforge.document.service;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.document.dto.DocumentResponse;
import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.repository.DocumentRepository;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chạy với Postgres thật (Testcontainers), để kiểm tra phần SQL mà unit test với mock không bắt được:
 * điều kiện deleted_at IS NULL, lọc theo user_id, ORDER BY created_at DESC và phân trang.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class DocumentQueryIntegrationTest {

    @Autowired DocumentService documentService;
    @Autowired DocumentRepository documentRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        alice = saveUser("sub-alice", "alice@test.com");
        bob = saveUser("sub-bob", "bob@test.com");
    }

    private UUID saveUser(String sub, String email) {
        return userRepository.saveAndFlush(User.builder().googleSub(sub).email(email).build()).getId();
    }

    /** created_at do @CreationTimestamp gán và updatable = false, nên phải sửa bằng native SQL. */
    private UUID saveDoc(UUID userId, String name, Instant createdAt) {
        Document d = documentRepository.saveAndFlush(Document.builder()
                .userId(userId)
                .originalFilename(name)
                .fileType(FileType.PDF)
                .fileSizeBytes(100)
                .fileHash("a".repeat(64))
                .storageKey("2026/10/" + UUID.randomUUID() + ".pdf")
                .build());
        em.createNativeQuery("UPDATE documents SET created_at = :t WHERE id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", d.getId())
                .executeUpdate();
        em.clear();
        return d.getId();
    }

    private void softDeleteDirectly(UUID id) {
        em.createNativeQuery("UPDATE documents SET deleted_at = now() WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
        em.clear();
    }

    private static void assertDocumentNotFound(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    // I1
    @Test
    void list_returnsOnlyOwnActiveDocuments_newestFirst() {
        saveDoc(alice, "old.pdf", NOW.minus(3, ChronoUnit.DAYS));
        saveDoc(alice, "new.pdf", NOW.minus(1, ChronoUnit.HOURS));
        saveDoc(alice, "mid.pdf", NOW.minus(1, ChronoUnit.DAYS));
        softDeleteDirectly(saveDoc(alice, "deleted.pdf", NOW));   // mới nhất nhưng đã xoá
        saveDoc(bob, "bob.pdf", NOW);                             // của người khác

        var page = documentService.list(alice, 0, 20);

        assertThat(page.content()).extracting(DocumentResponse::originalFilename)
                .containsExactly("new.pdf", "mid.pdf", "old.pdf");
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.hasNext()).isFalse();
    }

    // I2
    @Test
    void list_paginates() {
        saveDoc(alice, "a.pdf", NOW.minus(3, ChronoUnit.HOURS));
        saveDoc(alice, "b.pdf", NOW.minus(2, ChronoUnit.HOURS));
        saveDoc(alice, "c.pdf", NOW.minus(1, ChronoUnit.HOURS));

        var first = documentService.list(alice, 0, 2);
        var second = documentService.list(alice, 1, 2);

        assertThat(first.content()).extracting(DocumentResponse::originalFilename).containsExactly("c.pdf", "b.pdf");
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();

        assertThat(second.content()).extracting(DocumentResponse::originalFilename).containsExactly("a.pdf");
        assertThat(second.hasNext()).isFalse();
    }

    // I3
    @Test
    void list_userWithNoDocuments_returnsEmptyPage() {
        saveDoc(bob, "bob.pdf", NOW);

        var page = documentService.list(alice, 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    // I4
    @Test
    void get_otherUsersDocument_notFound() {
        UUID bobsDoc = saveDoc(bob, "bob.pdf", NOW);

        assertDocumentNotFound(() -> documentService.get(alice, bobsDoc));
    }

    // I5: row vẫn còn trong DB, chỉ biến mất khỏi API
    @Test
    void delete_softDeletes_rowStaysButIsHidden() {
        UUID id = saveDoc(alice, "lecture.pdf", NOW);

        documentService.delete(alice, id);
        em.flush();
        em.clear();

        Document row = documentRepository.findById(id).orElseThrow();
        assertThat(row.getDeletedAt()).isNotNull();
        assertDocumentNotFound(() -> documentService.get(alice, id));
        assertThat(documentService.list(alice, 0, 20).content()).isEmpty();
    }

    // I6
    @Test
    void delete_twice_secondCallNotFound() {
        UUID id = saveDoc(alice, "lecture.pdf", NOW);
        documentService.delete(alice, id);
        em.flush();
        em.clear();

        assertDocumentNotFound(() -> documentService.delete(alice, id));
    }

    // I7: xoá bằng id của người khác → 404 và document của họ còn nguyên
    @Test
    void delete_otherUsersDocument_notFoundAndUntouched() {
        UUID bobsDoc = saveDoc(bob, "bob.pdf", NOW);

        assertDocumentNotFound(() -> documentService.delete(alice, bobsDoc));
        em.flush();
        em.clear();

        assertThat(documentRepository.findById(bobsDoc).orElseThrow().getDeletedAt()).isNull();
        assertThat(documentService.get(bob, bobsDoc).originalFilename()).isEqualTo("bob.pdf");
    }
}
