package com.az.studyforge.document.repository;

import com.az.studyforge.document.entity.DocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {
    List<DocumentPage> findByDocumentIdOrderByPageNumber(UUID documentId);
}
