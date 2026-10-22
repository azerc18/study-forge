package com.az.studyforge.document.extraction;

import com.az.studyforge.document.entity.FileType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface TextExtractor {
    FileType supports();
    List<String> extractPages(InputStream in) throws IOException;
}
