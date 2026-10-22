package com.az.studyforge.document.service;

import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.common.utility.HashUtils;
import com.az.studyforge.document.dto.DocumentResponse;
import com.az.studyforge.document.dto.StoredFile;
import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.event.DocumentUploadedEvent;
import com.az.studyforge.document.repository.DocumentRepository;
import com.az.studyforge.document.filestorage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = DataSize.ofMegabytes(20).toBytes();
    private static final int MAX_PAGE_SIZE = 50;

    private final DocumentRepository documentRepository;
    private final FileTypeDetector fileTypeDetector;
    private final FileStorage fileStorage;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public DocumentResponse upload(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new AppException(ErrorCode.EMPTY_FILE);
        if (file.getSize() > MAX_FILE_SIZE) throw new AppException(ErrorCode.FILE_TOO_LARGE);

        FileType type = fileTypeDetector.detect(file);


        MessageDigest digest = HashUtils.newSha256();
        StoredFile stored;
        try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
            stored = fileStorage.save(in, type.extension());
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, e);
        }

        try {
            Document doc = documentRepository.save(Document.builder()
                    .userId(userId)
                    .originalFilename(sanitizeFilename(file.getOriginalFilename()))
                    .fileType(type)
                    .fileSizeBytes(stored.size())
                    .fileHash(HexFormat.of().formatHex(digest.digest()))
                    .storageKey(stored.key())
                    .build());

            eventPublisher.publishEvent(new DocumentUploadedEvent(doc.getId()));
            return DocumentResponse.from(doc);
        } catch (RuntimeException e) {
            fileStorage.delete(stored.key());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(UUID userId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        var result = documentRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                .map(DocumentResponse::from);
        return PageResponse.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse get(UUID userId, UUID documentId) {
        return DocumentResponse.from(findOwned(userId, documentId));
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID documentId) {
        findOwned(userId, documentId).markDeleted();
    }

    private Document findOwned(UUID userId, UUID documentId) {
        return documentRepository.findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private static String sanitizeFilename(String name) {
        String base = Paths.get(Objects.requireNonNullElse(name, "document")).getFileName().toString();
        return base.length() <= 255 ? base : base.substring(base.length() - 255);
    }
}
