package com.az.studyforge.document.event;

import com.az.studyforge.document.service.DocumentExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DocumentExtractionListener {

    private final DocumentExtractionService extractionService;

    @Async("extractionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        extractionService.extract(event.documentId());
    }
}
