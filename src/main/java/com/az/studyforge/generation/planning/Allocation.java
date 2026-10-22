package com.az.studyforge.generation.planning;

public final class Allocation {
    private Allocation() {}

    public static int[] proportional(int total, int[] weights) {
        int[] out = new int[weights.length];
        if (total == 0 || weights.length == 0) return out;

        long sum = 0;
        for (int w : weights) {
            if (w < 0) throw new IllegalArgumentException("weights must be >= 0");
            sum += w;
        }
        if (sum == 0) throw new IllegalArgumentException("weights must not all be 0");

        long cumulative = 0;
        int previous = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            int upTo = (int) Math.round((double) cumulative * total / sum);
            out[i] = upTo - previous;
            previous = upTo;
        }
        return out;
    }
}
