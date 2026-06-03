package com.homefit.core.scoring.normalize;

import com.homefit.core.domain.Dimension;
import com.homefit.core.scoring.ScoringMath;
import com.homefit.core.scoring.ScoringThresholds;
import org.springframework.stereotype.Component;

/** Proximity sub-score from travel minutes to the nearest amenity of a dimension's type. Runs offline. */
@Component
public class ProximityNormalizer {
    public int score(Dimension dimension, double travelMinutes) {
        double[] t = ScoringThresholds.PROXIMITY.get(dimension);
        if (t == null) throw new IllegalArgumentException("No proximity thresholds for " + dimension);
        return (int) Math.round(ScoringMath.bandScore(travelMinutes, t[0], t[1]));
    }
}
