package com.az.studyforge.admin.service;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.admin.dto.AdminUserRow;
import com.az.studyforge.admin.dto.UpdateUserRequest;
import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.repository.DocumentRepository;
import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.entity.QuizSet;
import com.az.studyforge.generation.repository.QuizSetRepository;
import com.az.studyforge.user.entity.Role;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingRunnable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Postgres thật. Mỗi lần chạy dùng 1 "marker" riêng trong email để tìm kiếm không dính user của test khác. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminUserServiceIntegrationTest {

    @Autowired AdminUserService service;
    @Autowired UserRepository userRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired QuizSetRepository quizSetRepository;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> createdUsers = new ArrayList<>();
    private final String marker = "adm" + UUID.randomUUID().toString().substring(0, 8);

    private UUID admin;
    private UUID alice;

    @BeforeEach
    void setUp() {
        admin = saveUser("boss", "Boss Admin", Role.ADMIN);
        alice = saveUser("alice", "Alice Nguyen", Role.USER);
    }

    @AfterEach
    void cleanUp() {
        createdUsers.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        createdUsers.clear();
    }

    // ===== list =====

    @Test
    void list_searchesEmailAndName_caseInsensitive() {
        saveUser("bob", "Bob Tran", Role.USER);

        assertThat(emails(service.list(marker.toUpperCase() + "-ALICE", 0, 20))).containsExactly(email("alice"));
        assertThat(emails(service.list("nguyen", 0, 100))).contains(email("alice")).doesNotContain(email("bob"));
        assertThat(emails(service.list(marker, 0, 20)))
                .containsExactlyInAnyOrder(email("boss"), email("alice"), email("bob"));
    }

    @Test
    void list_newestFirst_andPaged() {
        UUID bob = saveUser("bob", null, Role.USER);
        jdbc.update("UPDATE users SET created_at = now() - interval '1 hour' WHERE id IN (?, ?)", admin, alice);

        PageResponse<AdminUserRow> first = service.list(marker, 0, 1);

        assertThat(first.content()).extracting(AdminUserRow::id).containsExactly(bob);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
    }

    @Test
    void list_blankQuery_returnsEveryone_andSizeIsCapped() {
        PageResponse<AdminUserRow> page = service.list("   ", 0, 5000);

        assertThat(page.size()).isEqualTo(AdminUserService.MAX_PAGE_SIZE);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void list_countsQuizSets_includingDeleted_usedTodayOnlyToday() {
        UUID doc = saveDocument(alice);
        saveQuizSet(alice, doc, null);                                   // hôm nay
        saveQuizSet(alice, doc, Instant.now());                          // hôm nay, đã xoá → vẫn tính
        UUID old = saveQuizSet(alice, doc, null);
        jdbc.update("UPDATE quiz_sets SET created_at = now() - interval '3 days' WHERE id = ?", old);

        AdminUserRow row = only(service.list(marker + "-alice", 0, 20));

        assertThat(row.quizSetCount()).isEqualTo(3);
        assertThat(row.usedToday()).isEqualTo(2);
        AdminUserRow none = only(service.list(marker + "-boss", 0, 20));
        assertThat(none.quizSetCount()).isZero();
        assertThat(none.usedToday()).isZero();
    }

    // ===== update =====

    @Test
    void update_quotaOnly_keepsLockState() {
        AdminUserRow row = service.update(admin, alice, new UpdateUserRequest(42, null));

        assertThat(row.dailyQuota()).isEqualTo(42);
        assertThat(row.locked()).isFalse();
        assertThat(userRepository.findById(alice).orElseThrow().getDailyQuota()).isEqualTo(42);
    }

    @Test
    void update_lock_revokesRefreshTokens_thenUnlock() {
        insertRefreshToken(alice);
        insertRefreshToken(alice);

        AdminUserRow locked = service.update(admin, alice, new UpdateUserRequest(null, true));

        assertThat(locked.locked()).isTrue();
        assertThat(userRepository.findById(alice).orElseThrow().isLocked()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class, alice))
                .isZero();

        AdminUserRow unlocked = service.update(admin, alice, new UpdateUserRequest(null, false));
        assertThat(unlocked.locked()).isFalse();
    }

    @Test
    void update_emptyRequest_changesNothing() {
        AdminUserRow row = service.update(admin, alice, new UpdateUserRequest(null, null));

        assertThat(row.dailyQuota()).isEqualTo(10);
        assertThat(row.locked()).isFalse();
    }

    @Test
    void update_cannotLockSelf_orAnotherAdmin() {
        UUID otherAdmin = saveUser("boss2", null, Role.ADMIN);

        assertError(() -> service.update(admin, admin, new UpdateUserRequest(null, true)), ErrorCode.CANNOT_MODIFY_SELF);
        assertError(() -> service.update(admin, otherAdmin, new UpdateUserRequest(null, true)), ErrorCode.CANNOT_MODIFY_SELF);
        assertThat(userRepository.findById(admin).orElseThrow().isLocked()).isFalse();
        assertThat(userRepository.findById(otherAdmin).orElseThrow().isLocked()).isFalse();
    }

    @Test
    void update_adminCanChangeOwnQuota() {
        assertThat(service.update(admin, admin, new UpdateUserRequest(1000, null)).dailyQuota()).isEqualTo(1000);
    }

    @Test
    void update_unknownUser_throwsNotFound() {
        assertError(() -> service.update(admin, UUID.randomUUID(), new UpdateUserRequest(5, null)),
                ErrorCode.USER_NOT_FOUND);
    }

    // ===== helpers =====

    private String email(String name) {
        return marker + "-" + name + "@test.com";
    }

    private UUID saveUser(String name, String fullName, Role role) {
        UUID id = userRepository.save(User.builder()
                .googleSub("sub-" + UUID.randomUUID()).email(email(name)).fullName(fullName)
                .role(role).dailyQuota(10).build()).getId();
        createdUsers.add(id);
        return id;
    }

    private UUID saveDocument(UUID userId) {
        return documentRepository.save(Document.builder()
                .userId(userId).originalFilename("lecture.pdf").fileType(FileType.PDF).fileSizeBytes(1000)
                .fileHash("c".repeat(64)).storageKey("2026/10/" + UUID.randomUUID() + ".pdf")
                .status(DocumentStatus.READY).pageCount(5).wordCount(2000)
                .build()).getId();
    }

    private UUID saveQuizSet(UUID userId, UUID documentId, Instant deletedAt) {
        QuizSet set = quizSetRepository.save(QuizSet.builder()
                .userId(userId).documentId(documentId).title("Set")
                .questionType(QuestionType.MCQ).requestedCount(10).language(OutputLanguage.VI)
                .cacheKey(HashUtils.sha256Hex(UUID.randomUUID().toString())).promptVersion("test")
                .deletedAt(deletedAt)
                .build());
        return set.getId();
    }

    private void insertRefreshToken(UUID userId) {
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId, HashUtils.sha256Hex(UUID.randomUUID().toString()),
                Timestamp.from(Instant.now().plus(Duration.ofDays(7))));
    }

    private static List<String> emails(PageResponse<AdminUserRow> page) {
        return page.content().stream().map(AdminUserRow::email).toList();
    }

    private static AdminUserRow only(PageResponse<AdminUserRow> page) {
        assertThat(page.content()).hasSize(1);
        return page.content().getFirst();
    }

    private static void assertError(ThrowingRunnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
