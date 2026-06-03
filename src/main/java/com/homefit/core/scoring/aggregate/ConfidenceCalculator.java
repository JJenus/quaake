package com.homefit.core.scoring.aggregate;

import com.homefit.core.domain.Dimension;
import java.util.Map;
import java.util.Set;

/** Confidence = the fraction of (normalized) weight actually covered by available dimensions. */
@org.springframework.stereotype.Component
public class ConfidenceCalculator {
    public double forAvailable(Map<Dimension, Double> normalizedWeights, Set<Dimension> available) {
        double covered = 0;
        for (Dimension d : available) covered += normalizedWeights.getOrDefault(d, 0.0);
        return Math.min(1.0, covered);
    }
}
