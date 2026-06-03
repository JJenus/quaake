package com.homefit.core.domain;

import java.util.EnumMap;
import java.util.Map;

/** A user's raw priority weights. {@link #normalized()} rescales them to sum to 1. */
public record UserWeights(Map<Dimension, Double> raw) {

    public UserWeights {
        raw = new EnumMap<>(raw);
    }

    public Map<Dimension, Double> normalized() {
        double total = raw.values().stream().filter(v -> v != null && v > 0).mapToDouble(Double::doubleValue).sum();
        Map<Dimension, Double> out = new EnumMap<>(Dimension.class);
        if (total <= 0) return out;
        for (var e : raw.entrySet()) {
            double v = e.getValue() == null ? 0 : e.getValue();
            if (v > 0) out.put(e.getKey(), v / total);
        }
        return out;
    }
}
