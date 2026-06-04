package com.homefit.ingestion.pipeline;

import com.homefit.ingestion.adapter.OsmAmenityAdapter;
import com.homefit.ingestion.compute.SubScoreComputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Offline pipeline (worker profile): ingest the configured OSM box, then recompute sub-scores
 * for its cells and refresh the cell_profile view. Stage 1 of the deployment topology.
 */
@Component
@Profile("worker")
public class SubScoreComputeJob {

    private static final Logger log = LoggerFactory.getLogger(SubScoreComputeJob.class);

    private final OsmAmenityAdapter osm;
    private final SubScoreComputeService compute;

    public SubScoreComputeJob(OsmAmenityAdapter osm, SubScoreComputeService compute) {
        this.osm = osm;
        this.compute = compute;
    }

    @Scheduled(cron = "${homefit.ingestion.subscore-cron:0 0 3 * * *}")
    public void run() {
        var bbox = osm.defaultBBox();
        int amenities = osm.ingestBBox(bbox);
        int subscores = compute.recompute(osm.cellsFor(bbox));
        log.info("Pipeline complete: {} amenities ingested, {} sub-scores written", amenities, subscores);
    }
}
