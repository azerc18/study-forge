package com.az.studyforge.generation.llm.parse;

import com.az.studyforge.common.utility.HashUtils;

import java.text.Normalizer;
import java.util.Locale;

public final class QuestionNormalizer {
    private QuestionNormalizer() {}

    public static String hash(String text) {
        String n = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return HashUtils.sha256Hex(n);
    }
}
