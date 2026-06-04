package com.homefit.ingestion.model;

import java.util.Map;

/** A raw OSM element from Overpass (node/way/relation), with its centre coordinates and tags. */
public record OsmElement(String type, long id, double lat, double lon, Map<String, String> tags) {}
