package com.az.studyforge.feedback.dto;

public enum Verdict {
    MET(1.0), PARTIAL(0.5), MISSING(0.0);

    private final double weight;
    Verdict(double weight) { this.weight = weight; }
    public double weight() { return weight; }
}
