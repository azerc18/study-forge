package com.az.studyforge.document.extraction;

public final class TextNormalizer {
    private TextNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw
                .replace('\u0000', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    public static int countWords(String text) {
        return text.isBlank() ? 0 : text.strip().split("\\s+").length;
    }
}
