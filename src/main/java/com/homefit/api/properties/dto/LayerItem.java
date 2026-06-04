package com.homefit.api.properties.dto;

/** One row inside a layer: a sub-score and/or a proximity reading. Null fields are omitted by Jackson config. */
public record LayerItem(String key, String label, Integer subScore, Double confidence,
                        String sourceTier, Double travelMinutes, String travelMode) {}
