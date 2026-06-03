package com.homefit.api.properties.dto;

/** Minimal discovery card shape (sample until the read model is wired). */
public record PropertySummary(String id, String name, String areaLabel, String priceBand,
                              double lat, double lng, Integer fitScore) {}
