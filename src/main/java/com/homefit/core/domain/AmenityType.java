package com.homefit.core.domain;

import java.util.Optional;

/**
 * Amenity categories (mirrors the Postgres {@code amenity_type} enum). Finer-grained than
 * {@link Dimension}; {@link #dimension()} maps each to the dimension it contributes a sub-score to.
 */
public enum AmenityType {
    SCHOOL("school", Dimension.SCHOOLS),
    HOSPITAL("hospital", Dimension.HOSPITAL),
    CLINIC("clinic", Dimension.HOSPITAL),
    PHARMACY("pharmacy", null),               // shown, but not a weighted dimension in v1
    MARKET("market", Dimension.MARKETS),
    GROCERY("grocery", Dimension.MARKETS),
    PLACE_OF_WORSHIP("place_of_worship", Dimension.WORSHIP),
    TRANSIT_STOP("transit_stop", Dimension.TRANSIT),
    PARK("park", Dimension.PARKS),
    OTHER("other", null);

    private final String token;
    private final Dimension dimension;

    AmenityType(String token, Dimension dimension) {
        this.token = token;
        this.dimension = dimension;
    }

    public String token() { return token; }

    /** The scoring dimension this amenity type feeds, if any. */
    public Optional<Dimension> dimension() { return Optional.ofNullable(dimension); }

    public static AmenityType fromToken(String t) {
        for (AmenityType a : values()) if (a.token.equals(t)) return a;
        throw new IllegalArgumentException("Unknown amenity_type token: " + t);
    }
}
