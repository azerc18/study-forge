package com.az.studyforge.generation.planning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllocationTest {

    @Test
    void sumAlwaysEqualsTotal_randomized() {
        Random random = new Random(42);   // seed cố định → lỗi thì chạy lại vẫn ra đúng ca đó

        for (int run = 0; run < 1_000; run++) {
            int total = random.nextInt(60);
            int[] weights = random.ints(1 + random.nextInt(20), 0, 5_000).toArray();
            if (Arrays.stream(weights).sum() == 0) weights[0] = 1;

            int[] out = Allocation.proportional(total, weights);

            assertThat(Arrays.stream(out).sum())
                    .as("total=%d weights=%s", total, Arrays.toString(weights))
                    .isEqualTo(total);
            assertThat(Arrays.stream(out).allMatch(v -> v >= 0)).as("no negative share").isTrue();
        }
    }

    @Test
    void equalWeights_splitEvenly() {
        int[] weights = new int[10];
        Arrays.fill(weights, 1800);

        assertThat(Allocation.proportional(50, weights)).containsOnly(5);
    }

    @Test
    void fewerItemsThanSlots_spreadOut() {
        assertThat(Allocation.proportional(3, new int[]{1, 1, 1, 1, 1})).containsExactly(1, 0, 1, 0, 1);
    }

    @Test
    void proportionalToWeights() {
        assertThat(Allocation.proportional(10, new int[]{100, 300})).containsExactly(3, 7);
    }

    @Test
    void zeroWeight_getsNothing() {
        assertThat(Allocation.proportional(10, new int[]{0, 5, 5})).containsExactly(0, 5, 5);
    }

    @Test
    void zeroTotal_allZeros() {
        assertThat(Allocation.proportional(0, new int[]{3, 4, 5})).containsExactly(0, 0, 0);
    }

    @Test
    void emptyWeights_emptyResult() {
        assertThat(Allocation.proportional(5, new int[0])).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 50})
    void singleSlot_takesEverything(int total) {
        assertThat(Allocation.proportional(total, new int[]{123})).containsExactly(total);
    }

    @Test
    void negativeWeight_rejected() {
        assertThatThrownBy(() -> Allocation.proportional(5, new int[]{3, -1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allZeroWeights_rejected() {
        assertThatThrownBy(() -> Allocation.proportional(5, new int[]{0, 0}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
