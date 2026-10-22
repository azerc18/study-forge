package com.az.studyforge.document.filestorage;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.document.dto.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@Slf4j
public class LocalFileStorage implements FileStorage {

    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);
    private final Path root;

    public LocalFileStorage(StorageProperties props) throws IOException {
        this.root = props.localDir().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public StoredFile save(InputStream in, String extension) {
        String key = FOLDER.format(Instant.now()) + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            long size = Files.copy(in, target);
            return new StoredFile(key, size);
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", key, e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
        return path;
    }
}
