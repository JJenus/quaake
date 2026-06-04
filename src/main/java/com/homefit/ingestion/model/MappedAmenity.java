package com.homefit.ingestion.model;

import com.homefit.core.domain.AmenityType;
import java.util.Map;

/** An OSM element classified into one of our amenity types, ready to persist. */
public record MappedAmenity(String osmType, long osmId, AmenityType type, String subtype,
                            String name, double lat, double lng, Map<String, String> tags) {}
