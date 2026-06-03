package com.homefit.core.scoring.normalize;

import com.homefit.core.scoring.ScoringMath;
import org.springframework.stereotype.Component;

/**
 * Affordability sub-score: budget fit blended with local market value.
 * Runs at REQUEST TIME (needs the user's budget). See spec §5.
 */
@Component
public class PriceNormalizer {

    /** 100 comfortably under budget; ~60 at budget; 0 far over. */
    public double budgetScore(double price, double budget) {
        if (budget <= 0) return 0.0;
        double r = price / budget;
        if (r <= 0.7) return 100.0;
        if (r <= 1.0) return 60.0 + ScoringMath.bandScore(r, 0.7, 1.0) * 0.4; // 100 -> 60
        if (r <= 1.3) return ScoringMath.bandScore(r, 1.0, 1.3) * 0.6;        // 60 -> 0
        return 0.0;
    }

    /** marketPercentile in 0..1 (higher = pricier locally); may be null when unknown. */
    public int score(double price, double budget, Double marketPercentile) {
        double b = budgetScore(price, budget);
        if (marketPercentile == null) return (int) Math.round(b);
        double market = ScoringMath.bandScore(marketPercentile, 0.0, 1.0); // (1 - q) * 100
        return (int) Math.round(0.7 * b + 0.3 * market);
    }
}
