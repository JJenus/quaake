package com.homefit.ingestion.model;

import com.homefit.core.domain.AmenityType;

/** An amenity after it has a database id (used for proximity nearest-references). */
public record PersistedAmenity(long id, AmenityType type, double lat, double lng, long cellH3) {}
