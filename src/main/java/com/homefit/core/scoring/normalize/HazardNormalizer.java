package com.homefit.core.scoring.normalize;

import org.springframework.stereotype.Component;

/** Other-hazards sub-score: worst-case across hazards (a place is only as safe as its highest risk). */
@Component
public class HazardNormalizer {
    public int score(int... perHazardScores) {
        int min = 100;
        for (int s : perHazardScores) min = Math.min(min, s);
        return min;
    }
}
