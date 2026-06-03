package com.homefit.api.scoring;

import com.homefit.api.scoring.dto.*;
import com.homefit.core.domain.*;
import com.homefit.core.scoring.aggregate.WeightedAggregator;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Wires the real core scoring engine. /demo computes a Fit Score from supplied weights + sub-scores;
 * the production /properties/{id}/fit (see api-surface.md) will read sub-scores from PostGIS instead.
 */
@RestController
@RequestMapping("/api/v1/score")
public class ScoreController {

    private final WeightedAggregator aggregator;

    public ScoreController(WeightedAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @PostMapping("/demo")
    public FitResponse demo(@RequestBody DemoScoreRequest req) {
        Map<Dimension, Double> weights = new EnumMap<>(Dimension.class);
        for (WeightDto w : req.weights()) weights.put(Dimension.fromToken(w.dimension()), w.weight());

        Map<Dimension, DimensionScore> available = new EnumMap<>(Dimension.class);
        for (SubScoreDto s : req.subScores()) {
            Dimension d = Dimension.fromToken(s.dimension());
            available.put(d, new DimensionScore(d, s.subScore(), s.confidence(),
                    SourceTier.valueOf(s.sourceTier().toUpperCase())));
        }

        FitResult result = aggregator.aggregate(new UserWeights(weights), available);
        List<ContributionDto> breakdown = result.breakdown().stream()
                .map(c -> new ContributionDto(c.dimension().token(), round3(c.weight()), c.subScore(), round1(c.contribution())))
                .toList();
        return new FitResponse(result.score(), round3(result.confidence()), breakdown);
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
