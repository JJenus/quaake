package com.homefit.core.scoring;

/** The two normalization primitives every dimension uses. See fit-score-normalization-spec.md §2. */
public final class ScoringMath {
    private ScoringMath() {}

    public static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    /** Linear ramp: {@code xZero}->0, {@code xFull}->100, clamped. Direction is set by which bound is which. */
    public static double bandScore(double x, double xFull, double xZero) {
        if (xFull == xZero) return x == xFull ? 100.0 : 0.0;
        double t = (x - xZero) / (xFull - xZero);
        return clamp(t, 0.0, 1.0) * 100.0;
    }

    /** Exponential decay: 100 at x=0, decaying by {@code lambda}. Used for flood frequency. */
    public static double decayScore(double x, double lambda) {
        return 100.0 * Math.exp(-lambda * Math.max(0.0, x));
    }
}
