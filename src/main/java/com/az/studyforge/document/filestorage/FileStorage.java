package com.az.studyforge.document.filestorage;

import com.az.studyforge.document.dto.StoredFile;

import java.io.InputStream;

public interface FileStorage {
    StoredFile save(InputStream in, String extension);
    InputStream open(String key);
    void delete(String key);
}
