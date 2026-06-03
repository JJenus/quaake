package com.homefit.core.scoring.aggregate;

import com.homefit.core.domain.DealBreaker;
import com.homefit.core.domain.Dimension;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Applies deal-breakers BEFORE scoring. Returns true if the property passes all of them. */
@Component
public class HardFilterEvaluator {

    public boolean passes(List<DealBreaker> dealbreakers, double price, Map<Dimension, Integer> subScores) {
        if (dealbreakers == null) return true;
        for (DealBreaker db : dealbreakers) {
            switch (db.type()) {
                case "max_price" -> {
                    double max = asDouble(db.params().get("amount"));
                    if (price > max) return false;
                }
                case "min_subscore" -> {
                    Dimension d = Dimension.fromToken(String.valueOf(db.params().get("dimension")));
                    double min = asDouble(db.params().get("min"));
                    Integer s = subScores.get(d);
                    if (s == null || s < min) return false;
                }
                // "hospital_within_km" etc. -> evaluated from proximity data (TODO when wired)
                default -> { /* unknown filters are ignored, not failed */ }
            }
        }
        return true;
    }

    private static double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o));
    }
}
