package com.homefit.core.scoring.aggregate;

import com.homefit.core.domain.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Combines cached sub-scores with the user's weights at request time.
 * Missing dimensions are dropped and remaining weights renormalized (spec §11).
 */
@Component
public class WeightedAggregator {

    private final ConfidenceCalculator confidence;

    public WeightedAggregator(ConfidenceCalculator confidence) {
        this.confidence = confidence;
    }

    public FitResult aggregate(UserWeights weights, Map<Dimension, DimensionScore> available) {
        Map<Dimension, Double> norm = weights.normalized();
        double covered = available.keySet().stream().mapToDouble(d -> norm.getOrDefault(d, 0.0)).sum();

        List<Contribution> breakdown = new ArrayList<>();
        double total = 0;
        for (var e : available.entrySet()) {
            Dimension d = e.getKey();
            double w = norm.getOrDefault(d, 0.0);
            double wPrime = covered > 0 ? w / covered : 0;
            int sub = e.getValue().subScore();
            double contribution = wPrime * sub;
            total += contribution;
            breakdown.add(new Contribution(d, wPrime, sub, contribution));
        }
        breakdown.sort(Comparator.comparingDouble(Contribution::contribution).reversed());

        int fit = (int) Math.round(total);
        double conf = confidence.forAvailable(norm, available.keySet());
        return new FitResult(fit, conf, breakdown);
    }
}
