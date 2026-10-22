package com.az.studyforge.document.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.document.entity.FileType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class FileTypeDetector {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};   // "PK\3\4"
    private static final String PPTX_MARKER = "ppt/presentation.xml";

    public FileType detect(MultipartFile file) {
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);

        try {
            byte[] header;
            try (InputStream in = file.getInputStream()) {
                header = in.readNBytes(5);
            }
            if (name.endsWith(".pdf") && startsWith(header, PDF_MAGIC)) {
                return FileType.PDF;
            }
            if (name.endsWith(".pptx") && startsWith(header, ZIP_MAGIC) && containsPptxMarker(file)) {
                return FileType.PPTX;
            }
        } catch (IOException e) {
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE, e);
        }
        throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }


    private boolean containsPptxMarker(MultipartFile file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (PPTX_MARKER.equals(entry.getName())) return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
