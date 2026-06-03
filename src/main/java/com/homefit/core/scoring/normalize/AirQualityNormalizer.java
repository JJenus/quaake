package com.homefit.core.scoring.normalize;

import com.homefit.core.scoring.ScoringMath;
import com.homefit.core.scoring.ScoringThresholds;
import org.springframework.stereotype.Component;

/** Air-quality sub-score from PM2.5 (ug/m^3). Runs offline. */
@Component
public class AirQualityNormalizer {
    public int score(double pm25) {
        return (int) Math.round(
            ScoringMath.bandScore(pm25, ScoringThresholds.AIR_PM25_FULL, ScoringThresholds.AIR_PM25_ZERO));
    }
}
