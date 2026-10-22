package com.az.studyforge.document.extraction;

import com.az.studyforge.document.config.DocumentProperties;
import com.az.studyforge.document.entity.FileType;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PdfTextExtractor implements TextExtractor {

    private final DocumentProperties props;

    @Override
    public FileType supports() {
        return FileType.PDF;
    }

    @Override
    public List<String> extractPages(InputStream in) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(new RandomAccessReadBuffer(in))) {
            int pageCount = pdf.getNumberOfPages();
            if (pageCount > props.maxPages()) {
                throw new ExtractionException(
                        "Document has %d pages; the limit is %d.".formatted(pageCount, props.maxPages()));
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<String> pages = new ArrayList<>(pageCount);
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(stripper.getText(pdf));
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw new ExtractionException("Password-protected PDFs are not supported.");
        }
    }
}
