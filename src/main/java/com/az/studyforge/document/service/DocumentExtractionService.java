package com.az.studyforge.document.service;

import com.az.studyforge.document.entity.Document;
import com.az.studyforge.document.entity.DocumentPage;
import com.az.studyforge.document.entity.DocumentStatus;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.extraction.ExtractionException;
import com.az.studyforge.document.extraction.TextExtractor;
import com.az.studyforge.document.extraction.TextNormalizer;
import com.az.studyforge.document.repository.DocumentPageRepository;
import com.az.studyforge.document.repository.DocumentRepository;
import com.az.studyforge.document.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentExtractionService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final FileStorage fileStorage;
    private final List<TextExtractor> extractors;
    private final TransactionTemplate tx;

    public void extract(UUID documentId) {
        Document doc = tx.execute(status -> claim(documentId));
        if (doc == null) return;

        try {
            List<String> rawPages;
            try (InputStream in = fileStorage.open(doc.getStorageKey())) {
                rawPages = extractorFor(doc.getFileType()).extractPages(in);
            }

            List<DocumentPage> pages = toPages(documentId, rawPages);
            int totalWords = pages.stream().mapToInt(DocumentPage::getWordCount).sum();
            if (totalWords == 0) {
                throw new ExtractionException("No text found. Scanned PDFs are not supported yet.");
            }

            tx.executeWithoutResult(status -> markReady(documentId, pages, totalWords));   // ③
            log.info("Document {} extracted: {} pages, {} words", documentId, pages.size(), totalWords);

        } catch (ExtractionException e) {
            markFailed(documentId, e.getMessage());
        } catch (Exception e) {
            log.error("Extraction failed for document {}", documentId, e);
            markFailed(documentId, "Could not read this file. It may be corrupted.");
        }
    }

    private Document claim(UUID id) {
        return documentRepository.findById(id)
                .filter(d -> d.getStatus() == DocumentStatus.UPLOADED && d.getDeletedAt() == null)
                .map(d -> {
                    d.setStatus(DocumentStatus.EXTRACTING);
                    return d;
                })
                .orElse(null);
    }

    private TextExtractor extractorFor(FileType type) {
        return extractors.stream()
                .filter(e -> e.supports() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No extractor for " + type));
    }

    private static List<DocumentPage> toPages(UUID documentId, List<String> rawPages) {
        List<DocumentPage> pages = new ArrayList<>(rawPages.size());
        for (int i = 0; i < rawPages.size(); i++) {
            String content = TextNormalizer.normalize(rawPages.get(i));
            pages.add(DocumentPage.builder()
                    .documentId(documentId)
                    .pageNumber(i + 1)                  // giữ cả trang trống để đánh số trang luôn đúng
                    .content(content)
                    .wordCount(TextNormalizer.countWords(content))
                    .build());
        }
        return pages;
    }

    private void markReady(UUID id, List<DocumentPage> pages, int totalWords) {
        Document doc = documentRepository.findById(id).orElseThrow();
        pageRepository.saveAll(pages);
        doc.setPageCount(pages.size());
        doc.setWordCount(totalWords);
        doc.setErrorMessage(null);
        doc.setStatus(DocumentStatus.READY);
    }

    private void markFailed(UUID id, String message) {
        tx.executeWithoutResult(status -> documentRepository.findById(id).ifPresent(doc -> {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(message.length() <= 500 ? message : message.substring(0, 500));
        }));
    }
}
