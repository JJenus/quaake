package com.homefit.core.domain;

/** A normalized 0–100 sub-score for one dimension at one location, with its confidence and tier. */
public record DimensionScore(Dimension dimension, int subScore, double confidence, SourceTier tier) {
    public DimensionScore {
        if (subScore < 0 || subScore > 100) throw new IllegalArgumentException("subScore out of range: " + subScore);
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence out of range: " + confidence);
    }
}
