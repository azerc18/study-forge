package com.az.studyforge.document.repository;

import com.az.studyforge.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document,UUID> {
    Optional<Document> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    Page<Document> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
