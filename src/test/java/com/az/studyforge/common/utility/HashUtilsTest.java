package com.az.studyforge.utility;

import com.az.studyforge.common.utility.HashUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HashUtilsTest {
    private final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private final String REGEX = "^[0-9a-f]{64}$";

    @Test
    void sha256Hex_knownInput_returnsKnownHash() {
        assertThat(HashUtils.sha256Hex("abc")).isEqualTo(HASH);
    }

    @Test
    void sha256Hex_sameInput_sameOutput() {
        String first = HashUtils.sha256Hex("abc");
        String second = HashUtils.sha256Hex("abc");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void sha256Hex_alwaysReturns64LowercaseHexChars() {
        assertThat(HashUtils.sha256Hex("abc").matches(REGEX));
    }
}