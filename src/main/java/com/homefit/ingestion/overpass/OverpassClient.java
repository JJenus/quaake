package com.homefit.ingestion.overpass;

import com.homefit.ingestion.model.BBox;
import com.homefit.ingestion.model.OsmElement;
import java.util.List;

/** Fetches amenity elements for a bounding box from an Overpass endpoint. */
public interface OverpassClient {
    List<OsmElement> fetchAmenities(BBox bbox);
}
