package com.homefit.ingestion.adapter;

import com.homefit.core.geo.H3Support;
import com.homefit.ingestion.compute.ProximityComputer;
import com.homefit.ingestion.map.OsmAmenityMapper;
import com.homefit.ingestion.model.BBox;
import com.homefit.ingestion.model.MappedAmenity;
import com.homefit.ingestion.model.PersistedAmenity;
import com.homefit.ingestion.overpass.OverpassClient;
import com.homefit.ingestion.store.AmenityWriter;
import com.homefit.ingestion.store.ProximityWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One source end-to-end: OSM amenities -> ingest.amenity -> ingest.cell_proximity, for a
 * configured bounding box (default: a Lagos / Lekki area). Implements {@link SourceAdapter}.
 */
@Component
public class OsmAmenityAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(OsmAmenityAdapter.class);
    private static final int OSM_SOURCE_ID = 2;

    private final OverpassClient overpass;
    private final OsmAmenityMapper mapper;
    private final AmenityWriter amenityWriter;
    private final ProximityComputer proximityComputer;
    private final ProximityWriter proximityWriter;
    private final H3Support h3;
    private final BBox defaultBBox;

    public OsmAmenityAdapter(OverpassClient overpass, OsmAmenityMapper mapper,
                             AmenityWriter amenityWriter, ProximityComputer proximityComputer,
                             ProximityWriter proximityWriter, H3Support h3,
                             @Value("${homefit.ingestion.osm.bbox.min-lat:6.42}") double minLat,
                             @Value("${homefit.ingestion.osm.bbox.min-lng:3.45}") double minLng,
                             @Value("${homefit.ingestion.osm.bbox.max-lat:6.47}") double maxLat,
                             @Value("${homefit.ingestion.osm.bbox.max-lng:3.52}") double maxLng) {
        this.overpass = overpass;
        this.mapper = mapper;
        this.amenityWriter = amenityWriter;
        this.proximityComputer = proximityComputer;
        this.proximityWriter = proximityWriter;
        this.h3 = h3;
        this.defaultBBox = new BBox(minLat, minLng, maxLat, maxLng);
    }

    @Override
    public String sourceCode() { return "osm"; }

    @Override
    public int ingest(long adminRegionId) {
        return ingestBBox(defaultBBox);
    }

    /** Fetch + persist amenities, then compute + persist proximity for every cell in the box. */
    public int ingestBBox(BBox bbox) {
        var elements = overpass.fetchAmenities(bbox);
        List<MappedAmenity> mapped = elements.stream()
                .map(mapper::map).filter(java.util.Optional::isPresent).map(java.util.Optional::get).toList();
        List<PersistedAmenity> persisted = amenityWriter.upsert(mapped, OSM_SOURCE_ID);

        List<Long> cells = h3.cellsInBBox(bbox.minLat(), bbox.minLng(), bbox.maxLat(), bbox.maxLng(),
                H3Support.DEFAULT_RES);
        var proximity = proximityComputer.compute(cells, persisted);
        proximityWriter.upsert(proximity);

        log.info("OSM ingest: {} elements -> {} amenities, {} cells, {} proximity rows",
                elements.size(), persisted.size(), cells.size(), proximity.size());
        return persisted.size();
    }

    public List<Long> cellsFor(BBox bbox) {
        return h3.cellsInBBox(bbox.minLat(), bbox.minLng(), bbox.maxLat(), bbox.maxLng(), H3Support.DEFAULT_RES);
    }

    public BBox defaultBBox() { return defaultBBox; }
}
