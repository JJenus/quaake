package com.homefit.ingestion.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Offline job: read raw observations, run core's sub-score normalizers, write ingest.cell_subscore,
 * then refresh the cell_profile materialized view. Activates only under the "worker" profile
 * (Stage 1 of the deployment topology) — stub for now.
 */
@Component
@Profile("worker")
public class SubScoreComputeJob {

    private static final Logger log = LoggerFactory.getLogger(SubScoreComputeJob.class);

    @Scheduled(cron = "${homefit.ingestion.subscore-cron:0 0 3 * * *}")
    public void recomputeSubScores() {
        log.info("SubScoreComputeJob tick — TODO: read observations, run normalizers, upsert cell_subscore");
        // Next step (vertical slice): for each cell with new data ->
        //   FloodNormalizer/ProximityNormalizer/AirQualityNormalizer/... -> cell_subscore
        //   then REFRESH MATERIALIZED VIEW CONCURRENTLY ingest.cell_profile
    }
}
