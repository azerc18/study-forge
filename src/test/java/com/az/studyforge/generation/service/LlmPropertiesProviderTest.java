package com.az.studyforge.generation.service;

import com.az.studyforge.generation.llm.config.LlmProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LlmPropertiesProviderTest {

    private static LlmProperties.Provider provider(double in, double out) {
        return new LlmProperties.Provider("deepseek", "https://x", "key", "m", Duration.ofSeconds(90), 8000, 0.7,
                null, in, out);
    }

    @Test
    void costUsd_usesSeparateInputAndOutputPrices() {
        assertThat(provider(0.66, 1.98).costUsd(1_000_000, 500_000)).isCloseTo(1.65, within(1e-9));
    }

    @Test
    void costUsd_smallUsage_keepsFractions() {
        assertThat(provider(0.66, 1.98).costUsd(3_000, 1_000)).isCloseTo(0.00396, within(1e-12));
    }

    @Test
    void costUsd_freeProvider_isZero() {
        assertThat(provider(0, 0).costUsd(5_000_000, 5_000_000)).isZero();
    }
}
