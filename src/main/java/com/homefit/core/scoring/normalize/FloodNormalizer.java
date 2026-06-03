package com.homefit.core.scoring.normalize;

import com.homefit.core.scoring.ScoringMath;
import com.homefit.core.scoring.ScoringThresholds;
import org.springframework.stereotype.Component;

/** Flood sub-score from annualized flood frequency (events/year). Runs offline. */
@Component
public class FloodNormalizer {
    public int score(double annualFrequency) {
        return (int) Math.round(ScoringMath.decayScore(annualFrequency, ScoringThresholds.FLOOD_LAMBDA));
    }
}
