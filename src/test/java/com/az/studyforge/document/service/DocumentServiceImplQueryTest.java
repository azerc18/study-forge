package com.az.studyforge.document.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.document.dto.DocumentResponse;
import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.repository.DocumentRepository;
import com.az.studyforge.document.filestorage.FileStorage;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplQueryTest {

    @Mock DocumentRepository documentRepository;
    @Mock FileTypeDetector fileTypeDetector;
    @Mock FileStorage fileStorage;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks DocumentServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    private Document doc(String name) {
        return Document.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .originalFilename(name)
                .fileType(FileType.PDF)
                .fileSizeBytes(1234)
                .fileHash("a".repeat(64))
                .storageKey("2026/10/" + UUID.randomUUID() + ".pdf")
                .status(DocumentStatus.READY)
                .pageCount(3)
                .wordCount(820)
                .createdAt(Instant.parse("2026-10-20T10:00:00Z"))
                .build();
    }

    private static void assertDocumentNotFound(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    @AfterEach
    void queryMethodsNeverTouchStorageOrEvents() {
        verifyNoInteractions(fileStorage, eventPublisher, fileTypeDetector);
    }

    // ===== list =====

    @ParameterizedTest(name = "page={0}, size={1} -> page={2}, size={3}")
    @CsvSource({
            "-5, 1000, 0, 50",
            "0,  0,    0, 1",
            "2,  -3,   2, 1",
            "3,  20,   3, 20",
            "0,  50,   0, 50",
            "0,  51,   0, 50",
    })
    void list_clampsPageAndSize(int page, int size, int expectedPage, int expectedSize) {
        when(documentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(userId), any()))
                .thenAnswer(inv -> new PageImpl<Document>(List.of(), inv.getArgument(1), 0));

        var result = service.list(userId, page, size);

        assertThat(result.page()).isEqualTo(expectedPage);
        assertThat(result.size()).isEqualTo(expectedSize);
    }

    @Test
    void list_pageableHasNoSort() {
        when(documentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(userId), any()))
                .thenAnswer(inv -> new PageImpl<Document>(List.of(), inv.getArgument(1), 0));

        service.list(userId, 0, 20);

        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(documentRepository).findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(userId), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }

    // L3
    @Test
    void list_mapsPageToPageResponse() {
        Document first = doc("newer.pdf");
        Document second = doc("older.pdf");
        when(documentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 2), 5));

        var result = service.list(userId, 0, 2);

        assertThat(result.content()).extracting(DocumentResponse::id)
                .containsExactly(first.getId(), second.getId());
        assertThat(result.content().get(0).originalFilename()).isEqualTo("newer.pdf");
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
    }

    // ===== get =====

    @Test
    void get_ownDocument_returnsResponse() {
        Document d = doc("lecture.pdf");
        when(documentRepository.findByIdAndUserIdAndDeletedAtIsNull(d.getId(), userId))
                .thenReturn(Optional.of(d));

        DocumentResponse result = service.get(userId, d.getId());

        assertThat(result.id()).isEqualTo(d.getId());
        assertThat(result.originalFilename()).isEqualTo("lecture.pdf");
        assertThat(result.status()).isEqualTo(DocumentStatus.READY);
        assertThat(result.wordCount()).isEqualTo(820);
    }

    @Test
    void get_notFoundOrNotOwned_throwsDocumentNotFound() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)).thenReturn(Optional.empty());

        assertDocumentNotFound(() -> service.get(userId, id));
    }

    // ===== delete =====

    @Test
    void delete_ownDocument_setsDeletedAt() {
        Document d = doc("lecture.pdf");
        when(documentRepository.findByIdAndUserIdAndDeletedAtIsNull(d.getId(), userId))
                .thenReturn(Optional.of(d));

        Instant before = Instant.now();
        service.delete(userId, d.getId());

        assertThat(d.getDeletedAt()).isNotNull().isBetween(before, Instant.now());
        verify(documentRepository, never()).delete(any());
        verify(documentRepository, never()).deleteById(any());
    }

    // D2
    @Test
    void delete_notFoundOrNotOwned_throwsDocumentNotFound() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)).thenReturn(Optional.empty());

        assertDocumentNotFound(() -> service.delete(userId, id));
        verify(documentRepository, never()).save(any());
    }
}