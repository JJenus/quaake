package com.homefit.core.scoring.normalize;

import com.homefit.core.scoring.ScoringMath;
import org.springframework.stereotype.Component;

/** Safety sub-score. City/region-level; pair with reduced confidence. Runs offline. */
@Component
public class SafetyNormalizer {
    /** When the source is already a 0..100 safety index (higher = safer). */
    public int fromIndex(double safetyIndex) {
        return (int) Math.round(ScoringMath.clamp(safetyIndex, 0, 100));
    }
    /** When the source is a crime-rate percentile across regions (0..1, higher = more crime). */
    public int fromCrimePercentile(double percentile) {
        return (int) Math.round(ScoringMath.bandScore(percentile, 0.0, 1.0)); // (1 - p) * 100
    }
}
