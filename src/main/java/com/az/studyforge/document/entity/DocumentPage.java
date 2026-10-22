package com.az.studyforge.document.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "document_pages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "word_count", nullable = false)
    private int wordCount;
}
