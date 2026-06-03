package com.homefit.core.domain;

/** The user-weightable scoring axes. Tokens match the Postgres {@code dimension} enum and the API. */
public enum Dimension {
    FLOOD("flood"),
    SCHOOLS("schools"),
    AFFORDABILITY("affordability"),
    WORSHIP("worship"),
    AIR_QUALITY("air_quality"),
    SAFETY("safety"),
    MARKETS("markets"),
    HOSPITAL("hospital"),
    TRANSIT("transit"),
    PARKS("parks"),
    OTHER_HAZARDS("other_hazards");

    private final String token;
    Dimension(String token) { this.token = token; }
    public String token() { return token; }

    public static Dimension fromToken(String t) {
        for (Dimension d : values()) if (d.token.equals(t)) return d;
        throw new IllegalArgumentException("Unknown dimension token: " + t);
    }
}
