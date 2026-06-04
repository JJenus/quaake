package com.homefit.ingestion.model;

/** Axis-aligned geographic bounding box (WGS84 degrees). */
public record BBox(double minLat, double minLng, double maxLat, double maxLng) {}
