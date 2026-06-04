package com.homefit.ingestion.model;

import com.homefit.core.domain.AmenityType;

/** Computed nearest-amenity result for one cell + amenity type → row of ingest.cell_proximity. */
public record ProximityRow(long cellH3, AmenityType type, Long nearestAmenityId,
                           double travelMinutes, String travelMode, int distanceM,
                           int countWithinRadius, int radiusM) {}
