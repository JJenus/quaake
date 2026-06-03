package com.homefit;

import com.homefit.core.domain.*;
import com.homefit.core.scoring.aggregate.ConfidenceCalculator;
import com.homefit.core.scoring.aggregate.WeightedAggregator;
import com.homefit.core.scoring.normalize.FloodNormalizer;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests (no Spring context) — the core scoring logic against the spec's worked example. */
class ScoringSmokeTest {

    @Test
    void floodFrequencyDecaysAsSpecified() {
        FloodNormalizer flood = new FloodNormalizer();
        assertEquals(100, flood.score(0.0));      // never floods -> 100
        assertTrue(flood.score(0.10) >= 70 && flood.score(0.10) <= 78); // ~1-in-10 yrs -> ~74
    }

    @Test
    void weightedAggregateMatchesWorkedExample() {
        var aggregator = new WeightedAggregator(new ConfidenceCalculator());
        Map<Dimension, Double> w = new EnumMap<>(Dimension.class);
        w.put(Dimension.FLOOD, 0.30); w.put(Dimension.SCHOOLS, 0.25);
        w.put(Dimension.AFFORDABILITY, 0.20); w.put(Dimension.WORSHIP, 0.15);
        w.put(Dimension.AIR_QUALITY, 0.10);

        Map<Dimension, DimensionScore> a = new EnumMap<>(Dimension.class);
        a.put(Dimension.FLOOD, new DimensionScore(Dimension.FLOOD, 74, 1.0, SourceTier.MEASURED));
        a.put(Dimension.SCHOOLS, new DimensionScore(Dimension.SCHOOLS, 100, 1.0, SourceTier.MEASURED));
        a.put(Dimension.AFFORDABILITY, new DimensionScore(Dimension.AFFORDABILITY, 65, 1.0, SourceTier.MEASURED));
        a.put(Dimension.WORSHIP, new DimensionScore(Dimension.WORSHIP, 100, 1.0, SourceTier.MEASURED));
        a.put(Dimension.AIR_QUALITY, new DimensionScore(Dimension.AIR_QUALITY, 79, 1.0, SourceTier.MEASURED));

        FitResult r = aggregator.aggregate(new UserWeights(w), a);
        assertEquals(83, r.score());          // matches the spec's worked example
        assertEquals(1.0, r.confidence(), 0.001);
    }
}
