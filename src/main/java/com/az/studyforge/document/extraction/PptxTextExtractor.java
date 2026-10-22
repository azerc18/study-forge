package com.az.studyforge.document.extraction;

import com.az.studyforge.document.config.DocumentProperties;
import com.az.studyforge.document.entity.FileType;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PptxTextExtractor implements TextExtractor {

    private final DocumentProperties props;

    @Override
    public FileType supports() {
        return FileType.PPTX;
    }

    @Override
    public List<String> extractPages(InputStream in) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(in)) {
            List<XSLFSlide> slides = ppt.getSlides();
            if (slides.size() > props.maxPages()) {
                throw new ExtractionException(
                        "Presentation has %d slides; the limit is %d.".formatted(slides.size(), props.maxPages()));
            }

            List<String> pages = new ArrayList<>(slides.size());
            for (XSLFSlide slide : slides) {
                StringBuilder sb = new StringBuilder();
                appendShapes(slide.getShapes(), sb);
                pages.add(sb.toString());
            }
            return pages;
        }
    }

    private void appendShapes(List<XSLFShape> shapes, StringBuilder sb) {
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFTable table) {
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        sb.append(cell.getText()).append('\t');
                    }
                    sb.append('\n');
                }
            } else if (shape instanceof XSLFTextShape text) {
                sb.append(text.getText()).append('\n');
            } else if (shape instanceof XSLFGroupShape group) {
                appendShapes(group.getShapes(), sb);
            }
        }
    }
}
