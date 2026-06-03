package com.homefit.core.scoring;

import com.homefit.core.domain.Dimension;
import java.util.EnumMap;
import java.util.Map;

/**
 * Default scoring thresholds (starting values from the normalization spec).
 * Externalize/override per region in production (ingest.region_threshold_override).
 */
public final class ScoringThresholds {

    public static final double FLOOD_LAMBDA = 3.0;
    public static final double AIR_PM25_FULL = 5.0;     // WHO annual guideline -> 100
    public static final double AIR_PM25_ZERO = 150.0;   // hazardous -> 0

    /** Proximity {tIdeal, tMax} minutes per dimension. */
    public static final Map<Dimension, double[]> PROXIMITY = new EnumMap<>(Dimension.class);
    static {
        PROXIMITY.put(Dimension.SCHOOLS,  new double[]{10, 30});
        PROXIMITY.put(Dimension.WORSHIP,  new double[]{5,  30});
        PROXIMITY.put(Dimension.MARKETS,  new double[]{5,  25});
        PROXIMITY.put(Dimension.HOSPITAL, new double[]{10, 40});
        PROXIMITY.put(Dimension.TRANSIT,  new double[]{5,  20});
        PROXIMITY.put(Dimension.PARKS,    new double[]{10, 30});
    }

    private ScoringThresholds() {}
}
